/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.getopt.luke;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.GZIPOutputStream;

import javax.swing.JFileChooser;
import javax.swing.UIManager;

import org.apache.lucene.LucenePackage;
import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumberTools;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexReader.FieldOption;
import org.apache.lucene.index.IndexWriter.MaxFieldLength;
import org.apache.lucene.misc.SweetSpotSimilarity;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.payloads.PayloadNearQuery;
import org.apache.lucene.search.payloads.PayloadTermQuery;
import org.apache.lucene.search.similar.MoreLikeThis;
import org.apache.lucene.search.spans.SpanFirstQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.*;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.Version;
import org.apache.lucene.xmlparser.CoreParser;
import org.apache.lucene.xmlparser.CorePlusExtensionsParser;
import org.getopt.luke.DocReconstructor.Reconstructed;
import org.getopt.luke.decoders.BinaryDecoder;
import org.getopt.luke.decoders.DateDecoder;
import org.getopt.luke.decoders.Decoder;
import org.getopt.luke.decoders.NumDoubleDecoder;
import org.getopt.luke.decoders.NumFloatDecoder;
import org.getopt.luke.decoders.NumIntDecoder;
import org.getopt.luke.decoders.NumLongDecoder;
import org.getopt.luke.decoders.OldDateFieldDecoder;
import org.getopt.luke.decoders.OldNumberToolsDecoder;
import org.getopt.luke.decoders.StringDecoder;
import org.getopt.luke.plugins.ScriptingPlugin;
import org.getopt.luke.xmlQuery.XmlQueryParserFactory;
import org.getopt.luke.xmlQuery.CorePlusExtensionsParserFactory;

import thinlet.FrameLauncher;
import thinlet.Thinlet;

/**
 * This class allows you to browse a <a href="jakarta.apache.org/lucene">Lucene
 * </a> index in several ways - by document, by term, by query, and by most
 * frequent terms.
 * 
 * @author Andrzej Bialecki
 *  
 */
public class Luke extends Thinlet implements ClipboardOwner {

  private Directory dir = null;
  String pName = null;
  private IndexReader ir = null;
  private IndexSearcher is = null;
  private boolean slowAccess = false;
  private Collection<String> fn = null;
  private String[] idxFields = null;
  private HashMap<String, FieldTermCount> termCounts = new HashMap<String, FieldTermCount>();
  private List<LukePlugin> plugins = new ArrayList<LukePlugin>();
  private Object errorDlg = null;
  private Object infoDlg = null;
  private Object statmsg = null;
  private Object slowstatus = null;
  private Object slowmsg = null;
  private Analyzer stdAnalyzer = new StandardAnalyzer(Version.LUCENE_CURRENT);
  private Analyzer analyzer = null;
  //private QueryParser qp = null;
  private boolean readOnly = false;
  private boolean ram = false;
  private boolean keepCommits = false;
  private boolean multi = false;
  private int tiiDiv = 1;
  private IndexCommit currentCommit = null;
  private Similarity similarity = null;
  private Object lastST;
  private HashMap<String, Decoder> decoders = new HashMap<String, Decoder>();
  private Decoder defDecoder = new StringDecoder();
  
  /** Default salmon theme. */
  public static final int THEME_DEFAULT     = 0;
  /** Gray theme. */
  public static final int THEME_GRAY        = 1;
  /** Sandstone theme. */
  public static final int THEME_SANDSTONE   = 2;
  /** Sky blue theme. */
  public static final int THEME_SKY         = 3;
  /** Navy blue reverse theme. */
  public static final int THEME_NAVY        = 4;
  
  /** Theme color contants. */
  public int[][] themes = {
          {0xece9d0, 0x000000, 0xf5f4f0, 0x919b9a, 0xb0b0b0, 0xeeeeee, 0xb9b9b9, 0xff8080, 0xc5c5dd}, // default
          {0xe6e6e6, 0x000000, 0xffffff, 0x909090, 0xb0b0b0, 0xededed, 0xb9b9b9, 0x89899a, 0xc5c5dd}, // gray
          {0xeeeecc, 0x000000, 0xffffff, 0x999966, 0xb0b096, 0xededcb, 0xcccc99, 0xcc6600, 0xffcc66}, // sandstone
          {0xf0f0ff, 0x0000a0, 0xffffff, 0x8080ff, 0xb0b0b0, 0xededed, 0xb0b0ff, 0xff0000, 0xfde0e0}, // sky
          {0x6375d6, 0xffffff, 0x7f8fdd, 0xd6dff5, 0x9caae5, 0x666666, 0x003399, 0xff3333, 0x666666}  // navy
  };

  private int numTerms = 0;
  private static boolean exitOnDestroy = false;
  private Class[] analyzers = null;

  private String baseDir = null;

  private Class[] defaultAnalyzers = { SimpleAnalyzer.class, StandardAnalyzer.class, StopAnalyzer.class,
      WhitespaceAnalyzer.class };

  private static final String MSG_NOINDEX = "FAILED: No index, or index is closed. Reopen it.";
  private static final String MSG_READONLY = "FAILED: Read-Only index.";
  private static final String MSG_CONV_ERROR = "Some values could not be properly represented in this format. " + 
                      "They are marked in grey and presented as a hex dump.";

  /** Default constructor, loads preferences, initializes plugins and GUI. */ 
  public Luke() {
    super();
    Prefs.load();
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {}
    setTheme(Prefs.getInteger(Prefs.P_THEME, THEME_DEFAULT));
    String fontName = Prefs.getProperty(Prefs.P_FONT_NAME, "SansSerif");
    String fontSize = Prefs.getProperty(Prefs.P_FONT_SIZE, "12.0");
    float fsize = 12.0f;
    try {
      fsize = Float.parseFloat(fontSize);
    } catch (Exception e) {};
    Font f = new Font(fontName, Font.PLAIN, (int)fsize);
    setFont(f);
    addComponent(this, "/xml/luke.xml", null, null);
    errorDlg = addComponent(null, "/xml/error.xml", null, null);
    infoDlg = addComponent(null, "/xml/info.xml", null, null);
    statmsg = find("statmsg");
    slowstatus = find("slowstat");
    slowmsg = find(slowstatus, "slowmsg");
    // populate analyzers
    try {
      Class[] an = ClassFinder.getInstantiableSubclasses(Analyzer.class);
      if (an == null || an.length == 0) {
        analyzers = defaultAnalyzers;
      } else {
        HashSet<Class> uniq = new HashSet<Class>(Arrays.asList(an));
        analyzers = (Class[])uniq.toArray(new Class[uniq.size()]);
      }
      Object cbType = find("cbType");
      populateAnalyzers(cbType);
    } catch (Exception e) {
      e.printStackTrace();
    }
    loadPlugins();
    
  }

  /**
   * Set color theme for the UI.
   * @param which one of the predefined themes. For custom themes use {@link Thinlet#setColors(int, int, int, int, int, int, int, int, int)}.
   */
  public void setTheme(int which) {
    if (which < 0 || which >= themes.length) which = THEME_DEFAULT;
    int[] t = themes[which];
    setColors(t[0], t[1], t[2], t[3], t[4], t[5], t[6], t[7], t[8]);
    Prefs.setProperty(Prefs.P_THEME, which + "");
  }
  
  /**
   * Action handler to select color theme.
   * @param menu
   */
  public void actionTheme(Object menu) {
    String which = (String)getProperty(menu, "t");
    int t = THEME_DEFAULT;
    try {
      t = Integer.parseInt(which);
    } catch (Exception e) {};
    setTheme(t);
  }
  
  /**
   * Populate a combobox with the current list of analyzers.
   * @param combo
   */
  public void populateAnalyzers(Object combo) {
    removeAll(combo);
    String[] aNames = new String[analyzers.length];
    for (int i = 0; i < analyzers.length; i++) {
      aNames[i] = analyzers[i].getName();
    }
    Arrays.sort(aNames);
    for (int i = 0; i < aNames.length; i++) {
      Object choice = create("choice");
      setString(choice, "text", aNames[i]);
      add(combo, choice);
      if (i == 0) {
        setString(combo, "text", aNames[i]);
      }
    }
    int lastAnalyzerIdx = 0;
    String lastAnalyzer = Prefs.getProperty(Prefs.P_ANALYZER);
    if (lastAnalyzer != null) lastAnalyzerIdx = getIndex(combo, lastAnalyzer);
    if (lastAnalyzerIdx < 0) lastAnalyzerIdx = 0;
    setInteger(combo, "selected", lastAnalyzerIdx);
  }

  /**
   * Return an array of available Analyzer implementations.
   * @return
   */
  public Class[] getAnalyzers() {
    return analyzers;
  }

  /**
   * Loads plugins. Plugins are first searched from the CLASSPATH, and then from a
   * plugin list contained in a resource file "/.plugins". The "/.plugins" resource file
   * has a simple format - one fully qualified class name per line. Blank lines and
   * lines starting with '#' are ignored.
   */
  private void loadPlugins() {
    List pluginClasses = new ArrayList();
    // try to find all plugins
    try {
      Class classes[] = ClassFinder.getInstantiableSubclasses(LukePlugin.class);
      if (classes != null && classes.length > 0) {
        pluginClasses.addAll(Arrays.asList(classes));
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    // load plugins declared in the ".plugins" file
    try {
      InputStream is = getClass().getResourceAsStream("/.plugins");
      if (is != null) {
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String line = null;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("#")) continue;
          if (line.trim().equals("")) continue;
          try {
            Class clazz = Class.forName(line.trim());
            if (clazz.getSuperclass().equals(LukePlugin.class) && !pluginClasses.contains(clazz)) {
              pluginClasses.add(clazz);
            }
          } catch (Throwable x) {
            //
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      StringBuffer errors = new StringBuffer("Unable to load some plugins:");
      boolean failures = false;
      for (int i = 0; i < pluginClasses.size(); i++) {
        try {
          LukePlugin plugin = (LukePlugin) ((Class) pluginClasses.get(i)).getConstructor(new Class[0]).newInstance(
                  new Object[0]);
          String xul = plugin.getXULName();
          if (xul == null) continue;
          Object ui = parse(xul, plugin);
          plugin.setApplication(this);
          plugin.setMyUi(ui);
          plugins.add(plugin);
        } catch (Exception e) {
          failures = true;
          e.printStackTrace();
          errors.append("\n" + pluginClasses.get(i).toString());
        }
      }
      if (failures) {
        errorMsg(errors.toString());
      }
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg(e.toString());
    }
    if (plugins.size() == 0) return;
    initPlugins();
  }

  /**
   * Create UI for a single plugin.
   * @param tabs parent tabbedpane
   * @param plugin plugin instance
   */
  private void addPluginTab(Object tabs, LukePlugin plugin) {
    Object tab = create("tab");
    setColor(tab, "foreground", new Color(0x006000));
    setString(tab, "text", plugin.getPluginName());
    setFont(tab, getFont().deriveFont(Font.BOLD));
    add(tabs, tab);
    Object panel = create("panel");
    setInteger(panel, "gap", 2);
    setInteger(panel, "weightx", 1);
    setInteger(panel, "weighty", 1);
    setChoice(panel, "halign", "fill");
    setChoice(panel, "valign", "fill");
    setInteger(panel, "columns", 1);
    add(tab, panel);
    Object infobar = create("panel");
    setInteger(infobar, "gap", 8);
    setInteger(infobar, "top", 2);
    setInteger(infobar, "bottom", 2);
    setInteger(infobar, "weightx", 1);
    setChoice(infobar, "halign", "fill");
    setColor(infobar, "background", new Color(0xc0f0c0));
    add(panel, infobar);
    Object label = create("label");
    setString(label, "text", plugin.getPluginInfo());
    add(infobar, label);
    Object link = create("button");
    setChoice(link, "type", "link");
    setString(link, "text", plugin.getPluginHome());
    putProperty(link, "url", plugin.getPluginHome());
    setMethod(link, "action", "goUrl(this)", infobar, this);
    add(infobar, link);
    add(panel, create("separator"));
    add(panel, plugin.getMyUi());
  }

  /**
   * Return the list of active plugin instances.
   * @return
   */
  public List getPlugins() {
    return Collections.unmodifiableList(plugins);
  }
  
  /**
   * Get an already instantiated plugin, or null if such plugin was
   * not loaded on startup.
   * @param className fully qualified plugin classname
   * @return
   */
  public LukePlugin getPlugin(String className) {
    for (int i = 0; i < plugins.size(); i++) {
      Object plugin = plugins.get(i);
      if (plugin.getClass().getName().equals(className))
        return (LukePlugin)plugin;
    }
    return null;
  }
  
  Thread statusThread = null;
  long lastUpdate = 0;
  long statusSleep = 0;
  
  /**
   * Display a message on the status bar for 5 seconds.
   * @param msg message to display. Too long messages will be truncated by the UI.
   */
  public void showStatus(final String msg) {
    if (statusThread != null && statusThread.isAlive()) {
      setString(statmsg, "text", msg);
      statusSleep = 5000;
    } else {
      statusThread = new Thread() {
        public void run() {
          statusSleep = 5000;
          setString(statmsg, "text", msg);
          while (statusSleep > 0) {
            try {
              sleep(500);
            } catch (Exception e) {};
            statusSleep -= 500;
          }
          setString(statmsg, "text", "");
        }
      };
      statusThread.start();
    }
  }
  
  /**
   * As {@link #showStatus(String)} but also sets the "Last search time" label.
   * @param msg
   */
  public void showSearchStatus(String msg) {
    setString(lastST, "text", msg);
    showStatus(msg);
  }
  
  long lastSlowUpdate = 0L;
  long lastSlowCounter = 0L;
  Thread slowThread = null;
  long slowSleep = 0;
  
  public void showSlowStatus(final String msg, final long counter) {
    if (slowThread != null && slowThread.isAlive()) {
      lastSlowCounter += counter;
      setString(slowmsg, "text", msg + " " + lastSlowCounter);
      slowSleep = 5000;
    } else {
      slowThread = new Thread() {
        public void run() {
          slowSleep = 5000;
          lastSlowCounter = counter;
          setBoolean(slowstatus, "visible", true);
          setString(slowmsg, "text", msg + " " + lastSlowCounter);
          while (slowSleep > 0) {
            try {
              sleep(500);
            } catch (Exception e) {};
            slowSleep -= 500;
          }
          setString(slowmsg, "text", "");
          setBoolean(slowstatus, "visible", false);
        }
      };
      slowThread.start();
    }
  }

  /**
   * Add a Thinlet component from XUL file.
   * @param parent add the new component to this parent 
   * @param compView path to the XUL resource
   * @param handlerStr fully qualified classname of the handler to instantiate,
   * or null if the current class will become the handler
   * @param argv if not null, these arguments will be passed to the
   * appropriate constructor.
   * @return
   */
  public Object addComponent(Object parent, String compView, String handlerStr, Object[] argv) {
    Object res = null;
    Object handler = null;
    try {
      if (handlerStr != null) {
        if (argv == null) {
          handler = Class.forName(handlerStr).getConstructor(new Class[] { Thinlet.class }).newInstance(
                  new Object[] { this });
        } else {
          handler = Class.forName(handlerStr).getConstructor(new Class[] { Thinlet.class, Object[].class })
                  .newInstance(new Object[] { this, argv });
        }
      }
      if (handler != null) {
        res = parse(compView, handler);
      } else res = parse(compView);
      if (parent != null) {
        if (parent instanceof Thinlet)
          add(res);
        else add(parent, res);
      }
      return res;
    } catch (Exception exc) {
      exc.printStackTrace();
      errorMsg(exc.getMessage());
      return null;
    }
  }

  /**
   * Show a modal error dialog with OK button.
   * @param msg error message
   */
  public void errorMsg(String msg) {
    Object fMsg = find(errorDlg, "msg");
    setString(fMsg, "text", msg);
    add(errorDlg);
  }

  /**
   * Show a modal info dialog with OK button.
   * @param msg info message
   */
  public void infoMsg(String msg) {
    Object fMsg = find(infoDlg, "msg");
    setString(fMsg, "text", msg);
    add(infoDlg);
  }

  /**
   * Show an "Open Index" dialog.
   *
   */
  public void actionOpen() {
    Object dialog = addComponent(this, "/xml/lukeinit.xml", null, null);
    Object path = find(dialog, "path");
    if (this.baseDir != null)
      setString(path, "text", this.baseDir);
    else setString(path, "text", System.getProperty("user.dir"));
  }

  /**
   * Browse for a directory, and put the selection result in the
   * indicated widget.
   * @param path Thinlet widget to put the result
   */
  public void openBrowse(Object path) {
    JFileChooser fd = new JFileChooser();
    fd.setDialogType(JFileChooser.OPEN_DIALOG);
    fd.setDialogTitle("Select Index directory");
    fd.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fd.setFileHidingEnabled(false);
    String strPath = getString(path, "text");
    if (strPath != null) strPath.trim();
    if (strPath != null && strPath.length() > 0)
      fd.setCurrentDirectory(new File(strPath));
    else if (this.baseDir != null)
      fd.setCurrentDirectory(new File(this.baseDir));
    else fd.setCurrentDirectory(new File(System.getProperty("user.dir")));
    int res = fd.showOpenDialog(this);
    File iDir = null;
    if (res == JFileChooser.APPROVE_OPTION) iDir = fd.getSelectedFile();
    if (iDir != null && iDir.exists()) {
      if (!iDir.isDirectory()) iDir = iDir.getParentFile();
      setString(path, "text", iDir.toString());
    }
  }

  
  /**
   * Select an output file name, and put the selection result in the
   * indicated widget.
   * @param path Thinlet widget to put the result
   */
  public void saveBrowse(Object path, Object startButton) {
    JFileChooser fd = new JFileChooser();
    fd.setDialogType(JFileChooser.SAVE_DIALOG);
    fd.setDialogTitle("Select Output File");
    fd.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fd.setFileHidingEnabled(false);
    String strPath = getString(path, "text");
    if (strPath != null) strPath.trim();
    if (strPath != null && strPath.length() > 0)
      fd.setCurrentDirectory(new File(strPath));
    else if (this.baseDir != null)
      fd.setCurrentDirectory(new File(this.baseDir));
    else fd.setCurrentDirectory(new File(System.getProperty("user.dir")));
    int res = fd.showSaveDialog(this);
    setBoolean(startButton, "enabled", false);
    File iFile = null;
    if (res == JFileChooser.APPROVE_OPTION) iFile = fd.getSelectedFile();
    if (iFile != null) {
      setString(path, "text", iFile.toString());
      setBoolean(startButton, "enabled", true);
    }
  }

  
  /**
   * Initialize MRU list of indexes in the open index dialog.
   * @param dialog
   */
  public void setupInit(Object dialog) {
    Object path = find(dialog, "path");
    syncMRU(path);
  }

  /**
   * Attempt to load the index with parameters specified in the dialog.
   * <p>NOTE: this method is invoked from the UI. If you need to open an index
   * programmatically, you should use {@link #openIndex(String, boolean, boolean, boolean)} instead.</p>
   * @param dialog UI dialog with parameters
   */
  public void openOk(Object dialog) {
    Object path = find(dialog, "path");
    pName = getString(path, "text").trim();

    boolean force = getBoolean(find(dialog, "force"), "selected");
    boolean noReader = getBoolean(find(dialog, "cbNoReader"), "selected");
    tiiDiv = 1;
    try {
      tiiDiv = Integer.parseInt(getString(find(dialog, "tiiDiv"), "text"));
    } catch (Exception e) {
      e.printStackTrace();
    }
    Object dirImpl = getSelectedItem(find(dialog, "dirImpl"));
    String dirClass = null;
    if (dirImpl == null) {
      dirClass = FSDirectory.class.getName();
    } else {
      String name = getString(dirImpl, "name");
      if (name == null) {
        dirClass = getString(dirImpl, "text");
      } else {
        if (name.equals("fs")) {
          dirClass = FSDirectory.class.getName();
        } else if (name.equals("mmap")) {
          dirClass = MMapDirectory.class.getName();
        } else if (name.equals("niofs")) {
          dirClass = NIOFSDirectory.class.getName();
        }
      }
    }
    if (pName == null || pName.trim().equals("")) {
      errorMsg("Invalid path.");
      return;
    }
    readOnly = getBoolean(find(dialog, "ro"), "selected");
    ram = getBoolean(find(dialog, "ram"), "selected");
    keepCommits = getBoolean(find(dialog, "cbKeepCommits"), "selected");
    slowAccess = getBoolean(find(dialog, "cbSlowIO"), "selected");
    decoders.clear();
    currentCommit = null;
    Prefs.addToMruList(pName);
    syncMRU(path);
    remove(dialog);
    if (noReader) {
      removeAll();
      addComponent(this, "/xml/luke.xml", null, null);
      try {
        Directory d = openDirectory(dirClass, pName, false);
        if (IndexReader.indexExists(d)) {
          throw new Exception("there is no valid Lucene index in this directory.");
        }
        dir = d;
        initOverview();
        infoMsg("There is no IndexReader - most actions are disabled. " +
            "You can open IndexReader from current Directory using 'Re-Open'");
      } catch (Exception e) {
        errorMsg("ERROR: " + e.toString());
      }
    } else {
      openIndex(pName, force, dirClass, readOnly, ram, keepCommits, null, tiiDiv);
    }
  }
  
  public void actionClose() {
    if (ir != null) {
      try {
        if (is != null) is.close();
        ir.close();
        if (dir != null) dir.close();
      } catch (Exception e) {
        e.printStackTrace();
        errorMsg("Close failed: " + e.getMessage());
      }
    }
    ir = null;
    dir = null;
    is = null;
    removeAll();
    addComponent(this, "/xml/luke.xml", null, null);
    initPlugins();
  }
  
  public void actionCommit() {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    if (!IndexGate.hasChanges(ir)) {
      showStatus("No changes - commit ignored.");
      return;
    }
    Object dialog = addComponent(this, "/xml/commit.xml", null, null);
    Map userData = ir.getCommitUserData();
    TreeMap ud = new TreeMap(userData);
    putProperty(dialog, "userData", ud);
    _showUserData(dialog);
  }
  
  private void _showUserData(Object dialog) {
    Object table = find(dialog, "data");
    removeAll(table);
    Map<Object,Object> ud = (Map)getProperty(dialog, "userData");
    for (Entry e : ud.entrySet()) {
      Object row = create("row");
      putProperty(row, "key", e.getKey());
      add(table, row);
      Object cell = create("cell");
      setString(cell, "text", e.getKey().toString());
      add(row, cell);
      cell = create("cell");
      setString(cell, "text", e.getValue().toString());
      add(row, cell);
    }
  }
  
  public void putUserData(Object dialog) {
    Object key = find(dialog, "key");
    Object value = find(dialog, "value");
    String k = getString(key, "text");
    String v = getString(value, "text");
    if (k.equals("")) {
      showStatus("Cannot add empty key.");
      return;
    }    
    Map<Object,Object> ud = (Map)getProperty(dialog, "userData");
    ud.put(k, v);
    _showUserData(dialog);
  }
  
  public void deleteUserData(Object dialog) {
    Object table = find(dialog, "data");
    Map ud = (Map)getProperty(dialog, "userData");
    Object[] rows = getSelectedItems(table);
    if (rows == null || rows.length == 0) {
      return;
    }
    for (Object row : rows) {
      Object key = getProperty(row, "key");
      ud.remove(key);
    }
    _showUserData(dialog);
  }
  
  public void commitUserData(Object dialog) {
    Map userData = (Map)getProperty(dialog, "userData");
    remove(dialog);
    try {
      ir.flush(userData);
      initOverview();
      showFiles(dir, Collections.EMPTY_LIST);
    } catch (Exception e) {
      errorMsg("Error: " + e.toString());
    }
  }
  
  public void actionReopen() {
    if (dir == null) {
      return;
    }
    openIndex(pName, false, dir.getClass().getName(), readOnly, ram,
        keepCommits, currentCommit, tiiDiv);
  }
  /**
   * Open indicated index and re-initialize all GUI and plugins.
   * @param pName path to index
   * @param force if true, and the index is locked, unlock it first. If false, and
   * the index is locked, an error will be reported.
   * @param readOnly open in read-only mode, and disallow modifications.
   */
  public void openIndex(String name, boolean force, String dirImpl, boolean ro,
      boolean ramdir, boolean keepCommits, IndexCommit point, int tiiDivisor) {
    pName = name;
    readOnly = ro;
    removeAll();
    File baseFileDir = new File(name);
    this.baseDir = baseFileDir.toString();
    addComponent(this, "/xml/luke.xml", null, null);
    statmsg = find("statmsg");
    if (dir != null) {
      try {
        if (ir != null) ir.close();
      } catch (Exception e) {}
      ;
      try {
        if (dir != null) dir.close();
      } catch (Exception e) {}
      ;
    }
    ArrayList<Directory> dirs = new ArrayList<Directory>();
    try {
      Directory d = openDirectory(dirImpl, pName, false);
      if (IndexWriter.isLocked(d)) {
        if (ro) {
          errorMsg("Index is locked and Read-Only. Open for read-write and 'Force unlock'.");
          d.close();
          d = null;
          return;
        }
        if (force) {
          IndexWriter.unlock(d);
        } else {
          errorMsg("Index is locked. Try 'Force unlock' when opening.");
          d.close();
          d = null;
          return;
        }
      }
      boolean existsSingle = false;
      try {
        existsSingle = IndexReader.indexExists(d);
      } catch (Exception e) {
        //
      }
      
      if (!existsSingle) { // try multi
        File[] files = baseFileDir.listFiles();
        for (File f : files) {
          if (f.isFile()) {
            continue;
          }
          Directory d1 = openDirectory(dirImpl, f.toString(), false);
          if (IndexWriter.isLocked(d1)) {
            if (ro) {
              errorMsg("Index is locked and Read-Only. Open for read-write and 'Force unlock'.");
              d1.close();
              d1 = null;
              return;
            }
            if (force) {
              IndexWriter.unlock(d1);
            } else {
              errorMsg("Index is locked. Try 'Force unlock' when opening.");
              d1.close();
              d1 = null;
              return;
            }
          }
          existsSingle = false;
          try {
            existsSingle = IndexReader.indexExists(d1);
          } catch (Exception e) {};
          if (!existsSingle) {
            d1.close();
            continue;
          }
          dirs.add(d1);
        }
      } else {
        dirs.add(d);
      }
      
      if (dirs.size() == 0) {
        errorMsg("No valid directory at the location, try another location.");
        return;
      }

      if (ramdir) {
        showStatus("Loading index into RAMDirectory ...");
        Directory dir1 = new RAMDirectory();
        IndexWriter iw1 = new IndexWriter(dir1, new SimpleAnalyzer(), MaxFieldLength.UNLIMITED);
        iw1.addIndexesNoOptimize((Directory[])dirs.toArray(new Directory[dirs.size()]));
        iw1.close();
        showStatus("RAMDirectory loading done!");
        dir.close();
        dir = dir1;
      }
      IndexDeletionPolicy policy;
      if (keepCommits) {
        policy = new KeepAllIndexDeletionPolicy();
      } else {
        policy = new KeepLastIndexDeletionPolicy();
      }
      ArrayList<IndexReader> readers = new ArrayList<IndexReader>();
      for (Directory dd : dirs) {
        IndexReader reader;
        if (tiiDivisor > 1) {
          reader = IndexReader.open(dd, policy, ro, tiiDivisor);
        } else {
          reader = IndexReader.open(dd, policy, ro);
        }
        readers.add(reader);
      }
      if (readers.size() == 1) {
        ir = readers.get(0);
        dir = ir.directory();
      } else {
        ir = new MultiReader((IndexReader[])readers.toArray(new IndexReader[readers.size()]));
      }
      is = new IndexSearcher(ir);
      // XXX 
      slowAccess = false;
      initOverview();
      initPlugins();
      showStatus("Index successfully open.");
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg(e.getMessage());
      return;
    }
  }

  /**
   * This method attempts to work around the limitation in FSDirectory which
   * prevents you from switching Directory implementation during lifetime of a
   * JVM. Unfortunately, this doesn't work if a SecurityManager is present.
   * 
   * @param mmap if true use MMapDirectory, otherwise use FSDirectory
   * @param file index directory
   * @param create if true, create a new directory
   * @return directory implementation
   */
  Class defaultDirImpl = null;
  
  public FSDirectory openDirectory(String dirImpl, String file, boolean create) throws Exception {
    File f = new File(file);
    if (!f.exists()) {
      throw new Exception("Index directory doesn't exist.");
    }
    FSDirectory res = null;
    if (dirImpl == null || dirImpl.equals(FSDirectory.class.getName())) {
      return FSDirectory.open(f);
    }
    try {
      Class implClass = Class.forName(dirImpl);
      Constructor<FSDirectory> constr = implClass.getConstructor(File.class);
      res = constr.newInstance(f);
    } catch (Exception e) {
      errorMsg("Invalid directory implementation class: " + dirImpl);
      return null;
    }
    if (res != null) return res;
    // fall-back to FSDirectory.
    if (res == null) return FSDirectory.open(f);
    return null;
  }
  
  /**
   * Indicates whether I/O access should be optimized because
   * the index is on a slow medium (e.g. remote).
   * @return true if I/O access is costly and should be minimized
   */
  public boolean isSlowAccess() {
    return slowAccess;
  }
  
  /**
   * Set whether the I/O access to this index is costly and
   * should be minimized.
   */
  public void setSlowAccess(boolean slowAccess) {
    this.slowAccess = slowAccess;
    if (slowAccess) {
      
    }
  }

  /**
   * Initialize plugins. This method should always be called when a new index is open.
   *
   */
  public void initPlugins() {
    Object pluginsTabs = find("pluginsTabs");
    removeAll(pluginsTabs);
    for (int i = 0; i < plugins.size(); i++) {
      LukePlugin plugin = (LukePlugin) plugins.get(i);
      addPluginTab(pluginsTabs, plugin);
      plugin.setDirectory(dir);
      plugin.setIndexReader(ir);
      try {
        plugin.init();
      } catch (Exception e) {
        e.printStackTrace();
        showStatus("PLUGIN ERROR: " + e.getMessage());
      }
    }
  }

  /**
   * Initialize index overview and other GUI elements. This method is called always
   * when a new index is open.
   *
   */
  private void initOverview() {
    try {
      courier = new Font("Courier", getFont().getStyle(), getFont().getSize());
      lastST = find("lastST");
      setBoolean(find("bReload"), "enabled", true);
      setBoolean(find("bClose"), "enabled", true);
      setBoolean(find("bCommit"), "enabled", true);
      Object cbType = find("cbType");
      populateAnalyzers(cbType);
      Object pOver = find("pOver");
      Object iName = find("idx");
      String idxName;
      if (pName.length() > 40) {
        idxName = pName.substring(0, 10) + "..." + pName.substring(pName.length() - 27);
      } else {
        idxName = pName;
      }
      setString(iName, "text", idxName + (readOnly ? " (R)" : ""));
      iName = find(pOver, "iName");
      setString(iName, "text", pName + (readOnly ? " (Read-Only)" : ""));
      Object dirImpl = find("dirImpl");
      String implName = "N/A";
      if (dir == null) {
        if (ir != null) {
          implName = "N/A (reader is " + ir.getClass().getName() + ")";
        }
      } else {
        implName = dir.getClass().getName();
      }
      setString(dirImpl, "text", implName);
      Object fileSize = find("iFileSize");
      long totalFileSize = Util.calcTotalFileSize(pName, dir);
      setString(fileSize, "text", Util.normalizeSize(totalFileSize) + Util.normalizeUnit(totalFileSize));
      if (ir == null) {
        return;
      }      
      // we need IndexReader from now on
      Object iMod = find(pOver, "iMod");
      String modText = "N/A";
      if (dir != null) {
        modText = new Date(IndexReader.lastModified(dir)).toString();
      }
      setString(iMod, "text", modText);
      Object iDocs = find(pOver, "iDocs");
      String numdocs = String.valueOf(ir.numDocs());
      setString(iDocs, "text", numdocs);
      iDocs = find("iDocs1");
      setString(iDocs, "text", String.valueOf(ir.maxDoc() - 1));
      Object iFields = find(pOver, "iFields");
      fn = ir.getFieldNames(IndexReader.FieldOption.ALL);
      if (fn.size() == 0) {
        showStatus("Empty index.");
      }
      showFiles(dir, null);
      showCommits();
      final Object fList = find(pOver, "fList");
      final Object defFld = find("defFld");
      final Object fCombo = find("fCombo");
      TreeSet<String> fields = new TreeSet<String>(fn);
      idxFields = (String[])fields.toArray(new String[fields.size()]);
      setString(iFields, "text", String.valueOf(idxFields.length));
      final Object iTerms = find(pOver, "iTerms");
      if (!slowAccess) {
        Thread t = new Thread() {
          public void run() {
            Object r = create("row");
            Object cell = create("cell");
            add(r, cell);
            add(fList, r);
            setBoolean(cell, "enabled", false);
            setString(cell, "text", "..wait..");
            termCounts.clear();
            FieldTermCount ftc = null;
            try {
              TermEnum te = ir.terms();
              numTerms = 0;
              while (te.next()) {
                Term currTerm = te.term();
                if (ftc == null) {
                  // initialize
                  ftc = new FieldTermCount();
                  ftc.fieldname = currTerm.field();
                  termCounts.put(ftc.fieldname, ftc);
                }
                if (ftc.fieldname == currTerm.field()) {
                  ftc.termCount++;
                } else {
                  ftc = new FieldTermCount();
                  ftc.fieldname = currTerm.field();
                  ftc.termCount++;
                  termCounts.put(ftc.fieldname, ftc);
                }
                numTerms++;
              }
              te.close();
              setString(iTerms, "text", String.valueOf(numTerms));
              initFieldList(fList, fCombo, defFld);
            } catch (Exception e) {
              showStatus("ERROR: can't count terms per field");
            }
          }
        };
        t.start();
      } else {
        setString(iTerms, "text", "N/A");        
        initFieldList(fList, fCombo, defFld);
      }
      Object iDel = find(pOver, "iDelOpt");
      String sDel = ir.hasDeletions() ? "Yes (" + ir.numDeletedDocs() + ")" : "No";
      String sDelOpt = sDel + " / " +
                      (ir.isOptimized() ? "Yes" : "No");
      setString(iDel, "text", sDelOpt);
      Object iVer = find(pOver, "iVer");
      String verText = "N/A";
      if (dir != null) {
        verText = Long.toHexString(IndexReader.getCurrentVersion(dir));
      }
      setString(iVer, "text", verText);
      Object iFormat = find(pOver, "iFormat");
      Object iCaps = find(pOver, "iCaps");
      String formatText = "N/A";
      String formatCaps = "N/A";
      if (dir != null) {
        int format = IndexGate.getIndexFormat(dir);
        IndexGate.FormatDetails formatDetails = IndexGate.getFormatDetails(format);
        formatText = format + " (" + formatDetails.genericName + ")";
        formatCaps = formatDetails.capabilities;
      }
      setString(iFormat, "text", formatText);
      setString(iCaps, "text", formatCaps);
      Object iTiiDiv = find(pOver, "iTiiDiv");
      String divText = "N/A";
      // not available in Lucene 3.0
//      try {
//        divText = String.valueOf(ir.getTermInfosIndexDivisor());
//      } catch (UnsupportedOperationException uoe) {
//      }
      setString(iTiiDiv, "text", divText);
      Object iCommit = find(pOver, "iCommit");
      String commitText = "N/A";
      try {
        IndexCommit commit = ir.getIndexCommit();
        commitText = commit.getSegmentsFileName() + " (" +
          new Date(commit.getTimestamp()).toString() + ")";
      } catch (UnsupportedOperationException uoe) {
      }
      setString(iCommit, "text", commitText);
      Object iUser = find(pOver, "iUser");
      String userData = null;
      try {
        Map userDataMap = ir.getCommitUserData();
        if (userDataMap != null && !userDataMap.isEmpty()) {
          userData = ir.getCommitUserData().toString();
        } else {
          userData = "--";
        }
      } catch (UnsupportedOperationException uoe) {
        userData = "(not supported)";
      }
      setString(iUser, "text", userData);
      final Object nTerms = find("nTerms");
      if (!slowAccess) {
        Thread t = new Thread() {
          public void run() {
            actionTopTerms(nTerms);
          }
        };
        t.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg(e.getMessage());
    }
  }

  private void initFieldList(Object fList, Object fCombo, Object defFld) {
    removeAll(fList);
    removeAll(fCombo);
    removeAll(defFld);
    setString(fCombo, "text", idxFields[0]);
    setString(defFld, "text", idxFields[0]);
    NumberFormat intCountFormat = NumberFormat.getIntegerInstance();
    NumberFormat percentFormat = NumberFormat.getNumberInstance();
    intCountFormat.setGroupingUsed(true);
    percentFormat.setMaximumFractionDigits(2);
    // sort by names now
    for (String s : idxFields) {
      Object row = create("row");
      putProperty(row, "fName", s);
      add(fList, row);
      Object cell = create("cell");
      setString(cell, "text", s);
      add(row, cell);
      cell = create("cell");
      FieldTermCount ftc = termCounts.get(s);
      if (ftc != null) {
        long cnt = ftc.termCount;
        setString(cell, "text", intCountFormat.format(cnt));
        setChoice(cell, "alignment", "right");
        add(row, cell);
        float pcent = (float)(cnt * 100) / (float)numTerms;
        cell = create("cell");
        setString(cell, "text", percentFormat.format(pcent) + " %");
        setChoice(cell, "alignment", "right");
        add(row, cell);
      } else {
        setString(cell, "text", "0");
        setChoice(cell, "alignment", "right");
        add(row, cell);
        cell = create("cell");
        setString(cell, "text", "0.00 %");
        setChoice(cell, "alignment", "right");
        add(row, cell);
      }
      cell = create("cell");
      setChoice(cell, "alignment", "right");
      Decoder dec = decoders.get(s);
      if (dec == null) dec = defDecoder;
      setString(cell, "text", dec.toString());
      add(row, cell);
      // populate combos
      Object choice = create("choice");
      add(fCombo, choice);
      setString(choice, "text", s);
      putProperty(choice, "fName", s);
      choice = create("choice");
      add(defFld, choice);
      setString(choice, "text", s);
      putProperty(choice, "fName", s);
    }
    setString(find("defFld"), "text", idxFields[0]);
    // Remove columns
    Object header = get(find("sTable"), "header");
    removeAll(header);
    Object c = create("column");
    setString(c, "text", "#");
    setInteger(c, "width", 40);
    add(header, c);
    c = create("column");
    setString(c, "text", "Score");
    setInteger(c, "width", 50);
    add(header, c);
    c = create("column");
    setString(c, "text", "Doc. Id");
    setInteger(c, "width", 60);
    add(header, c);
    for (int j = 0; j < idxFields.length; j++) {
      c = create("column");
      setString(c, "text", idxFields[j]);
      add(header, c);
    }
  }
  
  private void showCommits() throws Exception {
    Object commitsTable = find("commitsTable");
    removeAll(commitsTable);
    if (dir == null) {
      Object row = create("row");
      Object cell = create("cell");
      setString(cell, "text", "<not available>");
      setBoolean(cell, "enabled", false);
      add(row, cell);
      add(commitsTable, row);
      return;
    }
    Collection commits = IndexReader.listCommits(dir);
    // commits are ordered from oldest to newest ?
    Iterator it = commits.iterator();
    int rowNum = 0;
    while (it.hasNext()) {
      IndexCommit commit = (IndexCommit)it.next();
      // figure out the name of the segment files
      Collection files = commit.getFileNames();
      Iterator itf = files.iterator();
      Object row = create("row");
      boolean enabled = rowNum < commits.size() - 1;
      Color color = null;
      rowNum++;
      add(commitsTable, row);
      putProperty(row, "commit", commit);
      if (enabled) {
        putProperty(row, "commitDeletable", Boolean.TRUE);
      }
      Object cell = create("cell");
      setString(cell, "text", String.valueOf(commit.getGeneration()));
      add(row, cell);
      cell = create("cell");
      char[] flags = new char[]{'-', '-'};
      if (commit.isDeleted()) flags[0] = 'D';
      if (commit.isOptimized()) flags[1] = 'O';
      setString(cell, "text", new String(flags));
      setFont(cell, "font", courier);
      setChoice(cell, "alignment", "center");
      add(row, cell);
      cell = create("cell");
      setString(cell, "text", Long.toHexString(commit.getVersion()));
      add(row, cell);
      cell = create("cell");
      setString(cell, "text", new Date(commit.getTimestamp()).toString());
      add(row, cell);
      cell = create("cell");
      Map userData = commit.getUserData();
      if (userData != null && !userData.isEmpty()) {
        setString(cell, "text", userData.toString());
      } else {
        setString(cell, "text", "--");
      }
      add(row, cell);
    }
  }
  
  public void showCommitFiles(Object commitTable) throws Exception {
    List commits = new ArrayList();
    Object[] rows = getSelectedItems(commitTable);
    if (rows == null || rows.length == 0) {
      showFiles(dir, commits);
      return;
    }
    for (int i = 0; i < rows.length; i++) {
      IndexCommit commit = (IndexCommit)getProperty(rows[i], "commit");
      if (commit != null) {
        commits.add(commit);
      }
    }
    showFiles(dir, commits);
  }

  private void showFiles(Directory dir, List commits) throws Exception {
    Object filesTable = find("filesTable");
    if (dir == null) {
      removeAll(filesTable);
      Object row = create("row");
      Object cell = create("cell");
      setString(cell, "text", "<not available>");
      setBoolean(cell, "enabled", false);
      add(row, cell);
      add(filesTable, row);
      return;
    }
    String[] physFiles = dir.listAll();
    List<String> files = new ArrayList();
    if (commits != null && commits.size() > 0) {
      for (int i = 0; i < commits.size(); i++) {
        IndexCommit commit = (IndexCommit)commits.get(i);
        files.addAll(commit.getFileNames());
      }
    } else {
      files.addAll(Arrays.asList(physFiles));
    }
    Collections.sort(files);
    List segs = getIndexFileNames(dir);
    List dels = getIndexDeletableNames(dir);
    removeAll(filesTable);
    for (int i = 0; i < files.size(); i++) {
      String fileName = files.get(i);
      String pathName;
      if (pName.endsWith(File.separator)) {
        pathName = pName;
      } else {
        pathName = pName + File.separator;
      }
      File file = new File(pathName + fileName);
      Object row = create("row");
      Object nameCell = create("cell");
      setString(nameCell, "text", fileName);
      add(row, nameCell);
      Object sizeCell = create("cell");
      setString(sizeCell, "text", Util.normalizeSize(file.length()));
      setChoice(sizeCell, "alignment", "right");
      add(row, sizeCell);
      Object unitCell = create("cell");
      setString(unitCell, "text", Util.normalizeUnit(file.length()));
      add(row, unitCell);
      boolean deletable = dels.contains(fileName.intern());
      String inuse = getFileFunction(fileName);
      Object delCell = create("cell");
      setString(delCell, "text", deletable ? "YES" : "-");
      add(row, delCell);
      Object inuseCell = create("cell");
      setString(inuseCell, "text", inuse);
      add(row, inuseCell);
      add(filesTable, row);
    }
  }
  
  private String getFileFunction(String file) {
    String res = IndexGate.getFileFunction(file);
    if (res == null) {
      res = "YES";
    }
    return res;
  }

  private void syncMRU(Object path) {
    removeAll(path);
    for (Iterator iter = Prefs.getMruList().iterator(); iter.hasNext();) {
      String element = (String) iter.next();
      Object choice = create("choice");
      setString(choice, "text", element);
      add(path, choice);
    }
  }

  /**
   * Update the list of top terms.
   * @param nTerms Thinlet widget containing the number of top terms to show
   */
  public void actionTopTerms(Object nTerms) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    String sndoc = getString(nTerms, "text");
    int nd = 50;
    try {
      nd = Integer.parseInt(sndoc);
    } catch (Exception e) {}
    final int ndoc = nd;
    Object[] fields = getSelectedItems(find("fList"));
    String[] flds = null;
    if (fields == null || fields.length == 0) {
      flds = idxFields;
    } else {
      flds = new String[fields.length];
      for (int i = 0; i < fields.length; i++) {
        flds[i] = (String) getProperty(fields[i], "fName");
      }
    }
    final String[] fflds = flds;
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          TermInfo[] tis = HighFreqTerms.getHighFreqTerms(ir, null, ndoc + 1, fflds);
          Object table = find("tTable");
          removeAll(table);
          if (tis == null || tis.length == 0) {
            Object row = create("row");
            Object cell = create("cell");
            add(row, cell);
            cell = create("cell");
            add(row, cell);
            cell = create("cell");
            add(row, cell);
            cell = create("cell");
            setBoolean(cell, "enabled", false);
            setString(cell, "text", "No Results");
            add(row, cell);
            add(table, row);
            return;
          }
          for (int i = 0; i < tis.length; i++) {
            Object row = create("row");
            add(table, row);
            putProperty(row, "term", tis[i].term);
            putProperty(row, "ti", tis[i]);
            Object cell = create("cell");
            setChoice(cell, "alignment", "right");
            setString(cell, "text", String.valueOf(i + 1));
            add(row, cell);
            cell = create("cell");
            setChoice(cell, "alignment", "right");
            setString(cell, "text", String.valueOf(tis[i].docFreq) + "  ");
            add(row, cell);
            cell = create("cell");
            setString(cell, "text", tis[i].term.field());
            add(row, cell);
            cell = create("cell");
            Decoder dec = decoders.get(tis[i].term.field());
            if (dec == null) dec = defDecoder;
            String s;
            try {
              s = dec.decodeTerm(tis[i].term.field(), tis[i].term.text());
            } catch (Throwable e) {
              s = tis[i].term.text();
              setColor(cell, "foreground", Color.RED);
            }
            setString(cell, "text", "  " + s);
            add(row, cell);
          }
        } catch (Exception e) {
          e.printStackTrace();
          errorMsg(e.getMessage());
        }
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }
  
  public void clipTopTerms(Object tTable) {
    Object[] rows = getItems(tTable);
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < rows.length; i++) {
      TermInfo ti = (TermInfo)getProperty(rows[i], "ti");
      if (ti == null) continue;
      sb.append(ti.docFreq + "\t" + ti.term.field() + "\t" + ti.term.text() + "\n");
    }
    StringSelection sel = new StringSelection(sb.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, this);
  }

  /**
   * Switch to a view that shows all documents containing selected term.
   * @param tTable Thinlet table widget, where selected row contains a property named "term",
   * which is the selected {@link Term} instance
   */
  public void browseTermDocs(Object tTable) {
    Object row = getSelectedItem(tTable);
    if (row == null) return;
    Term t = (Term) getProperty(row, "term");
    if (t == null) return;
    Object tabpane = find("maintpane");
    setInteger(tabpane, "selected", 1);
    _showTerm(find("fCombo"), find("fText"), t);
    repaint();

  }

  public void showTermDocs(Object tTable) {
    Object row = getSelectedItem(tTable);
    if (row == null) return;
    Term t = (Term) getProperty(row, "term");
    if (t == null) return;
    Object tabpane = find("maintpane");
    setInteger(tabpane, "selected", 2);
    Object qField = find("qField");
    setString(qField, "text", t.field() + ":" + t.text());
    search(qField);
    repaint();

  }

  /**
   * Undelete all deleted documents in the current index. This method also
   * updates the overview.
   */
  public void actionUndelete() {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    try {
      ir.undeleteAll();
      initOverview();
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg(e.getMessage());
    }
  }

  /** Not implemented yet... */
  public void actionConvert(Object method) {

  }
  
  public List<String> getIndexDeletableNames(Directory d) {
    if (d == null) return null;
    List<String> deletable = null;
    try {
      deletable = IndexGate.getDeletableFiles(d);
    } catch (Exception e) {
      e.printStackTrace();
    }
    //for (int i = 0; i < deletable.size(); i++) {
    //  System.out.println(" -del " + deletable.get(i));
    //}
    if (deletable == null) deletable = Collections.EMPTY_LIST;
    return deletable;
  }
  
  public Directory getDirectory() {
    return dir;
  }
  
  public IndexReader getIndexReader() {
    return ir;
  }
  
  public void setIndexReader(IndexReader reader, String indexName) {
    if (reader == null) {
      return;
    }
    try {
      if (is != null) {
        is.close();
        is = null;
      }
      if (ir != null) {
        ir.close();
        ir = null;
      }
      if (dir != null) {
        dir.close();
        dir = null;
      }
      try {
        dir = reader.directory();
      } catch (UnsupportedOperationException uoe) {
        dir = null;
      }
      ir = reader;
      is = new IndexSearcher(ir);
      pName = indexName;
      initOverview();
      initPlugins();
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg("Setting new IndexReader failed: " + e.toString());
    }
  }
  public List<String> getIndexFileNames(Directory d) {
    if (d == null) return null;
    List<String> names = null;
    try {
      names = IndexGate.getIndexFiles(d);
    } catch (Exception e) {
      e.printStackTrace();
    }
    //for (int i = 0; i < names.size(); i++) {
    //  System.out.println(" -seg " + names.get(i));
    //}
    return names;
  }
  
  public void actionCheckIndex() {
    if (dir == null) {
      errorMsg("No directory - open index directory first (you may use the 'no IndexReader' option).");
      return;
    }
    Object dialog = addComponent(null, "/xml/checkindex.xml", null, null);    
    Object dirName = find(dialog, "dirName");
    setString(dirName, "text", pName);
    add(dialog);
  }
  
  public void checkIndex(final Object dialog) {
    Thread t = new Thread() {
      public void run() {
        Object panel = find(dialog, "msg");
        Object fixPanel = find(dialog, "fixPanel");
        PanelPrintWriter ppw = new PanelPrintWriter(Luke.this, panel);
        Object ckRes = find(dialog, "ckRes");
        CheckIndex.Status status = null;
        CheckIndex ci = new CheckIndex(dir);
        ci.setInfoStream(ppw);
        putProperty(dialog, "checkIndex", ci);
        putProperty(dialog, "ppw", ppw);
        try {
          status = ci.checkIndex();
        } catch (Exception e) {
          ppw.println("ERROR: caught exception, giving up.\n\n");
          e.printStackTrace();
          e.printStackTrace(ppw);
        }
        if (status != null) {
          Luke.this.putProperty(dialog, "checkStatus", status);
          String statMsg;
          if (status.clean) {
            statMsg = "OK";
          } else if (status.toolOutOfDate) {
            statMsg = "ERROR: Can't check - tool out-of-date";
          } else {
            // show fixPanel
            setBoolean(fixPanel, "visible", true);
            repaint(dialog);
            statMsg = "BAD: ";
            if (status.cantOpenSegments) {
              statMsg += "cantOpenSegments ";
            }
            if (status.missingSegments) {
              statMsg += "missingSegments ";
            }
            if (status.missingSegmentVersion) {
              statMsg += "missingSegVersion ";
            }
            if (status.numBadSegments > 0) {
              statMsg += "numBadSegments=" + status.numBadSegments + " ";
            }
            if (status.totLoseDocCount > 0) {
              statMsg += "lostDocCount=" + status.totLoseDocCount + " ";
            }
          }
          setString(ckRes, "text", statMsg);
        }
      }
    };
    t.start();
  }
  
  public void fixIndex(final Object dialog) {
    Thread t = new Thread() {
      public void run(){
        CheckIndex ci = (CheckIndex)getProperty(dialog, "checkIndex");
        if (ci == null) {
          errorMsg("You need to run 'Check Index' first.");
          return;
        }
        CheckIndex.Status status = (CheckIndex.Status)getProperty(dialog, "checkStatus");
        if (status == null) {
          errorMsg("You need to run 'Check Index' first.");
          return;
        }
        Object fixRes = find(dialog, "fixRes");
        PanelPrintWriter ppw = (PanelPrintWriter)getProperty(dialog, "ppw");
        try {
          ci.fixIndex(status);
          setString(fixRes, "text", "DONE. Review the output above.");
        } catch (Exception e) {
          ppw.println("\nERROR during Fix Index:");
          e.printStackTrace(ppw);
          setString(fixRes, "text", "FAILED. Review the output above.");
        }
      }
    };
    t.start();
  }
  
  public boolean isFSBased(Directory dir) {
    if (dir == null) {
      return false;
    }
    if (dir instanceof MMapDirectory ||
        dir instanceof NIOFSDirectory ||
        dir instanceof FSDirectory) {
      return true;
    }
    return false;
  }
  
  
  /**
   * This method will cleanup the current Directory of any content
   * that is not the part of the index.
   */
  public void actionCleanup() {
    if (dir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    try {
      List allFiles;
      boolean phys = false;
      if (isFSBased(dir)) {
        File phyDir = new File(pName);
        phys = true;
        allFiles = new ArrayList(Arrays.asList(phyDir.list()));
      } else {
        allFiles = new ArrayList(Arrays.asList(dir.listAll()));
      }
      List indexFiles = getIndexFileNames(dir);
      allFiles.removeAll(indexFiles);
      if (allFiles.size() == 0) {
        infoMsg("There are no files to be cleaned - target directory contains only index-related files.");
        return;
      }
      Object dialog = addComponent(null, "/xml/cleanup.xml", null, null);
      Object filesTable = find(dialog, "filesTable");
      for (int i = 0; i < allFiles.size(); i++) {
        String fileName = (String)allFiles.get(i);
        Object row = create("row");
        add(filesTable, row);
        putProperty(row, "fileName", fileName);
        long size = dir.fileLength(fileName);
        Object cell;
        cell = create("cell");
        setString(cell, "text", Util.normalizeSize(size));
        add(row, cell);
        cell = create("cell");
        setString(cell, "text", Util.normalizeUnit(size));
        add(row, cell);
        cell = create("cell");
        setString(cell, "text", fileName);
        add(row, cell); 
      }
      add(dialog);
    } catch (Exception e) {
      errorMsg("Error: " + e.toString());
    }
  }
  
  public void toggleKeep(Object filesTable) {
    Object[] rows = getSelectedItems(filesTable);
    if (rows == null || rows.length == 0) return;
    for (int i = 0; i < rows.length; i++) {
      Boolean Deletable = (Boolean)getProperty(rows[i], "deletable");
      boolean deletable = Deletable != null ? Deletable.booleanValue() : true;
      deletable = !deletable;
      putProperty(rows[i], "deletable", Boolean.valueOf(deletable));
      Object[] cells = getItems(rows[i]);
      for (int k = 0; k < cells.length; k++) {
        if (!deletable) {
          setBoolean(cells[k], "enabled", false);
        } else {
          setBoolean(cells[k], "enabled", true);
        }
      }
    }
    repaint(filesTable);
  }
  
  public void _actionCleanup(Object filesTable) {
    try {
      ir.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
    ArrayList noDel = new ArrayList();
    String errMsg = null;
    boolean phys = isFSBased(dir);
    int deleted = 0;
    try {
      Object[] rows = getItems(filesTable);
      for (int i = 0; i < rows.length; i++) {
        String name = (String)getProperty(rows[i], "fileName");
        Boolean Deletable = (Boolean)getProperty(rows[i], "deletable");
        boolean deletable = Deletable != null ? Deletable.booleanValue() : true;
        if (!deletable) {
          continue;
        }
        if (phys) {
          File f = new File(pName, name);
          if (!removeFile(f)) {
            noDel.add(name);
          }
          deleted++;
          continue;
        } else {
          dir.deleteFile(name);
          deleted++;
        }
      }
      if (noDel.size() > 0) {
        StringBuffer msg = new StringBuffer("Some files could not be deleted:");
        for (int i = 0; i < noDel.size(); i++) msg.append("\n" + noDel.get(i));
        errMsg = msg.toString();
      }
    } catch (Exception e) {
      e.printStackTrace();
      errMsg = "FAILED: " + e.getMessage();
    } finally {
      try {
        actionReopen();
      } catch (Exception e) {
        e.printStackTrace();
        errMsg = "FAILED: " + e.getMessage();
      }
    }
    if (errMsg != null) {
      errorMsg(errMsg);
    } else if (deleted == 0) {
      infoMsg("No files were deleted.");
    }
    
  }
  
  /** Recursively remove files and directories including the indicated
   * root file.
   * @param f root file name
   * @return true if successful, false otherwise. Note that if the result
   * is false the tree may have been partially removed.
   */
  public boolean removeFile(File f) {
    System.out.println("remove " + f);
    if (!f.isDirectory()) return f.delete();
    File[] files = f.listFiles();
    boolean res = true;
    if (files != null) {
      for (int i = 0; i < files.length; i++) {
        res = res && removeFile(files[i]);
      }
    }
    res = res && f.delete();
    return res;
  }
  
  public void actionExport() {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    Object dialog = addComponent(this, "/xml/export.xml", null, null);
  }
  
  public void export(final Object dialog) {
    Object ckOver = find(dialog, "ckOver");
    Object ckGzip = find(dialog, "ckGzip");
    Object path = find(dialog, "path");
    Object ckRanges = find(dialog, "ckRanges");
    Object list = find(dialog, "ranges");
    String fileName = getString(path, "text");
    fileName = fileName.trim();
    if (fileName.length() == 0) {
      errorMsg("No output file set.");
      return;
    }
    boolean over = getBoolean(ckOver, "selected");
    boolean gzip = getBoolean(ckGzip, "selected");
    if (gzip && !fileName.endsWith(".gz")) {
      fileName = fileName + ".gz";
      setString(path, "text", fileName);
    }
    File out = new File(fileName);
    if (out.exists()) {
      if (out.isDirectory()) {
        errorMsg("Output already exists and is a directory.");
        return;
      }
      if (!over) {
        errorMsg("Output already exists.");
        return;
      }
    }
    Ranges ranges = null;
    if (getBoolean(ckRanges, "selected")) {
      String rs = getString(list, "text");
      if (rs.trim().length() > 0) {
        try {
          ranges = Ranges.parse(rs);
        } catch (Exception e) {
          errorMsg(e.toString());
          return;
        }
      }
    }
    final Object progressBar = find(dialog, "bar");
    final Object counter = find(dialog, "counter");
    final Object msg = find(dialog, "msg");
    Observer obs = new Observer() {
      public void update(Observable o, Object arg) {
        ProgressNotification pn = (ProgressNotification)arg;
        setInteger(progressBar, "minimum", pn.minValue);
        setInteger(progressBar, "maximum", pn.maxValue);
        setInteger(progressBar, "value", pn.curValue);
        setString(msg, "text", pn.message);
        if (!pn.aborted) {
          setString(counter, "text", pn.curValue + " of " + pn.maxValue + " done.");
        } else {
          setString(counter, "text", "ABORTED at " + pn.curValue + " of " + pn.maxValue);
        }
      }
    };
    // toggle buttons
    setBoolean(find(dialog, "startButton"), "visible", false);
    setBoolean(find(dialog, "abortButton"), "visible", true);
    setBoolean(find(dialog, "closeButton"), "enabled", false);
    _runExport(out, gzip, obs, dialog, ranges);
  }
  
  XMLExporter exporter = null;
  
  public void _runExport(final File out, final boolean gzip, Observer obs,
      final Object dialog, final Ranges ranges) {
    exporter = new XMLExporter(ir, pName);
    exporter.addObserver(obs);
    Thread t = new Thread() {
      public void run() {
        OutputStream os = null;
        try {
          os = new FileOutputStream(out);
          if (gzip) {
            os = new GZIPOutputStream(os);
          }
          exporter.export(os, true, true, "index", ranges);
          exporter = null;
        } catch (Exception e) {
          e.printStackTrace();
          errorMsg("ERROR occurred, file may be incomplete: " + e.toString());
        } finally {
          setBoolean(find(dialog, "startButton"), "visible", true);
          setBoolean(find(dialog, "abortButton"), "visible", false);
          setBoolean(find(dialog, "closeButton"), "enabled", true);
          if (os != null) {
            try {
              os.flush();
              os.close();
            } catch (Exception e) {
              errorMsg("ERROR closing output, file may be incomplete: " + e.toString());
            }
          }
        }
      }
    };
    t.start();
  }
  
  public void abortExport(Object dialog) {
    if (exporter != null && exporter.isRunning()) {
      exporter.abort();
    }
  }
  
  /**
   * Optimize the current index
   * @param method Thinlet menuitem widget containing the choice of index format.
   * If the widget name is "optCompound" then the index will be optimized into compound
   * format; otherwise a plain multi-file format will be used.
   * <p>NOTE: this method is usually invoked from the GUI, and it also re-initializes GUI
   * and plugins.</p>
   */
  public void actionOptimize() {
    if (dir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    Object dialog = addComponent(null, "/xml/optimize.xml", null, null);
    setString(find(dialog, "dirName"), "text", pName);
    add(dialog);
  }

  /**
   * Optimize the index.
   */
  public void optimize(final Object dialog) {
    Thread t = new Thread() {
      public void run() {
        IndexWriter iw = null;
        Object optimizeButton = find(dialog, "optimizeButton");
        setBoolean(optimizeButton, "enabled", false);
        Object closeButton = find(dialog, "closeButton");
        setBoolean(closeButton, "enabled", false);
        Object msg = find(dialog, "msg");
        Object stat = find(dialog, "stat");
        setString(stat, "text", "Running ...");
        PanelPrintWriter ppw = new PanelPrintWriter(Luke.this, msg);
        boolean useCompound = getBoolean(find(dialog, "optCompound"), "selected");
        boolean expunge = getBoolean(find(dialog, "optExpunge"), "selected");
        Object tiiSpin = find(dialog, "tii");
        Object segnumSpin = find(dialog, "segnum");
        int tii = Integer.parseInt(getString(tiiSpin, "text"));
        int segnum = Integer.parseInt(getString(segnumSpin, "text"));
        try {
          if (is != null) is.close();
          if (ir != null) ir.close();
          IndexDeletionPolicy policy;
          if (keepCommits) {
            policy = new KeepAllIndexDeletionPolicy();
          } else {
            policy = new KeepLastIndexDeletionPolicy();
          }
          iw = new IndexWriter(dir, new org.apache.lucene.analysis.WhitespaceAnalyzer(),
              false, policy, MaxFieldLength.UNLIMITED);
          iw.setUseCompoundFile(useCompound);
          iw.setTermIndexInterval(tii);
          iw.setInfoStream(ppw);
          long startSize = Util.calcTotalFileSize(pName, dir);
          long startTime = System.currentTimeMillis();
          if (expunge) {
            iw.expungeDeletes();
          } else {
            if (segnum > 1) {
              iw.optimize(segnum);
            } else {
              iw.optimize();
            }
          }
          long endTime = System.currentTimeMillis();
          long endSize = Util.calcTotalFileSize(pName, dir);
          long deltaSize = startSize - endSize;
          String sign = deltaSize > 0 ? " Increased " : " Reduced ";
          String sizeMsg = sign + Util.normalizeSize(Math.abs(deltaSize)) + Util.normalizeUnit(Math.abs(deltaSize));
          String timeMsg = String.valueOf(endTime - startTime) + " ms";
          showStatus(sizeMsg + " in " + timeMsg);
          iw.close();
          setString(stat, "text", "Finished OK.");
        } catch (Exception e) {
          e.printStackTrace(ppw);
          setString(stat, "text", "ERROR - aborted.");
          errorMsg("ERROR optimizing: " + e.toString());
          if (iw != null) try {
            iw.close();
          } catch (Exception e1) {}
        } finally {
          setBoolean(closeButton, "enabled", true);
        }
        try {
          actionReopen();
          is = new IndexSearcher(ir);
          // add dialog again
          add(dialog);
        } catch (Exception e) {
          e.printStackTrace(ppw);
          errorMsg("ERROR reopening after optimize:\n" + e.getMessage());
        }
      }
    };
    t.start();
  }

  public void showPrevDoc(Object docNum) {
    _showDoc(docNum, -1);
  }

  public void showNextDoc(Object docNum) {
    _showDoc(docNum, +1);
  }

  public void showDoc(Object docNum) {
    _showDoc(docNum, 0);
  }

  Document doc = null;
  int iNum;
  
  private void _showDoc(Object docNum, int incr) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    String num = getString(docNum, "text");
    if (num.trim().equals("")) num = String.valueOf(-incr);
    try {
      iNum = Integer.parseInt(num);
      iNum += incr;
      if (iNum < 0 || iNum >= ir.maxDoc()) {
        showStatus("Document number outside valid range.");
        return;
      }
      setString(docNum, "text", String.valueOf(iNum));
      if (!ir.isDeleted(iNum)) {
        SlowThread st = new SlowThread(this) {
          public void execute() {
            try {
              doc = ir.document(iNum);
              _showDocFields(iNum, doc);
            } catch (Exception e) {
              e.printStackTrace();
              showStatus(e.getMessage());
            }
          }
        };
        if (slowAccess) {
          st.start();
        } else {
          st.execute();
        }
      } else {
        showStatus("Deleted document - not available.");
        _showDocFields(iNum, null);
      }
    } catch (Exception e) {
      e.printStackTrace();
      showStatus(e.getMessage());
    }
  }
  
  public void actionAddDocument(Object docTable) {
    if (ir == null) {
      errorMsg(MSG_NOINDEX);
      return;
    }
    Object dialog = addComponent(null, "/xml/editdoc.xml", null, null);
    remove(find(dialog, "bDelAdd"));
    Object cbAnalyzers = find(dialog, "cbAnalyzers");
    populateAnalyzers(cbAnalyzers);
    setInteger(cbAnalyzers, "selected", 0);
    add(dialog);
  }

  public void actionReconstruct(Object docNumText) {
    final int[] nums = new int[1];
    try {
      String numString = getString(docNumText, "text");
      nums[0] = Integer.parseInt(numString);
    } catch (Exception e) {
      showStatus("ERROR: no valid document selected");
      return;
    }
    final Progress progress = new Progress(this);
    progress.setMessage("Reconstructing ...");
    progress.show();
    Thread thr = new Thread() {
      public void run() {
        try {
          int docNum = nums[0];
          DocReconstructor recon = new DocReconstructor(ir, idxFields, numTerms);
          recon.addObserver(progress);
          Reconstructed doc = recon.reconstruct(docNum);
          Object dialog = addComponent(null, "/xml/editdoc.xml", null, null);
          putProperty(dialog, "docNum", new Integer(docNum));
          Object cbAnalyzers = find(dialog, "cbAnalyzers");
          populateAnalyzers(cbAnalyzers);
          setInteger(cbAnalyzers, "selected", 0);
          Object editTabs = find(dialog, "editTabs");
          setString(find(dialog, "docNum"), "text", "Fields of Doc #: " + docNum);
          for (int p = 0; p < idxFields.length; p++) {
            String key = idxFields[p];
            if (!doc.hasField(key)) continue;
            Field[] fields = doc.getStoredFields().get(key);
            GrowableStringArray recField = doc.getReconstructedFields().get(key);
            int count = 0;
            if (recField != null) count = 1;
            if (fields != null && fields.length > count) count = fields.length;
            for (int i = 0; i < count; i++) {
              if (i > 0) recField = null; // show it only for the first field
              Object tab = create("tab");
              setString(tab, "text", key);
              setFont(tab, getFont().deriveFont(Font.BOLD));
              add(editTabs, tab);
              Object editfield = addComponent(tab, "/xml/editfield.xml", null, null);
              Object fType = find(editfield, "fType");
              Object sText = find(editfield, "sText");
              Object rText = find(editfield, "rText");
              Object fBoost = find(editfield, "fBoost");
              Object cbStored = find(editfield, "cbStored");
              //Object cbCmp = find(editfield, "cbCmp");
              Object cbBin = find(editfield, "cbBin");
              Object cbIndexed = find(editfield, "cbIndexed");
              Object cbTokenized = find(editfield, "cbTokenized");
              Object cbTVF = find(editfield, "cbTVF");
              Object cbTVFp = find(editfield, "cbTVFp");
              Object cbTVFo = find(editfield, "cbTVFo");
              Object cbONorms = find(editfield, "cbONorms");
              Object cbOTF = find(editfield, "cbOTF");
              Object stored = find(editfield, "stored");
              Object restored = find(editfield, "restored");
              setBoolean(cbONorms, "selected", !ir.hasNorms(key));
              Field f = null;
              if (fields != null && fields.length > i) {
                f = (Field) fields[i];
                setString(fType, "text", "Original stored field content");
                String text;
                if (f.isBinary()) {
                  text = Util.bytesToHex(f.getBinaryValue(),
                      f.getBinaryOffset(), f.getBinaryLength(), true);
                  setBoolean(cbBin, "selected", true);
                } else {
                  text = f.stringValue();
                }
                setString(sText, "text", text);
                setString(fBoost, "text", String.valueOf(f.getBoost()));
                setBoolean(cbStored, "selected", f.isStored());
                // Lucene 3.0 doesn't support compressed fields
                //setBoolean(cbCmp, "selected", false);
                setBoolean(cbIndexed, "selected", f.isIndexed());
                setBoolean(cbTokenized, "selected", f.isTokenized());
                setBoolean(cbTVF, "selected", f.isTermVectorStored());
                setBoolean(cbTVFp, "selected", f.isStorePositionWithTermVector());
                setBoolean(cbTVFo, "selected", f.isStoreOffsetWithTermVector());
                setBoolean(cbOTF, "selected", f.getOmitTermFreqAndPositions());
              } else {
                remove(stored);
              }
              if (recField != null) {
                String sep = " ";
                if (f == null) {
                  setString(fType, "text", "RESTORED content ONLY - check for errors!");
                  setColor(fType, "foreground", Color.red);
                } else {
                  setBoolean(rText, "editable", false);
                  setBoolean(rText, "border", false);
                  setString(restored, "text", "Tokenized (from all '" + key + "' fields)");
                  sep = ", ";
                }
                setBoolean(cbIndexed, "selected", true);
                setString(fBoost, "text", String.valueOf(1.0f));
                setString(rText, "text", recField.toString(sep));
              } else {
                remove(restored);
              }
            }
          }
          add(dialog);
          getPreferredSize(editTabs);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }
        progress.hide();
      }
    };
    thr.start();
  }

  public boolean actionEditAdd(Object editdoc) {
    Document doc = new Document();
    Object cbAnalyzers = find(editdoc, "cbAnalyzers");
    Analyzer a = new StandardAnalyzer(Version.LUCENE_CURRENT);
    String clazz = null;
    try {
      clazz = getString(cbAnalyzers, "text");
      a = (Analyzer) Class.forName(clazz).newInstance();
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg("Could not instantiate Analyzer '" + clazz + "'. Please select a valid Analyzer.");
      return false;
    }
    Object editTabs = find(editdoc, "editTabs");
    Object[] tabs = getItems(editTabs);
    for (int i = 0; i < tabs.length; i++) {
      String name = getString(tabs[i], "text");
      if (name.trim().equals("")) continue;
      Object fBoost = find(tabs[i], "fBoost");
      Object fText = find(tabs[i], "sText");
      if (fText == null) fText = find(tabs[i], "rText");
      Object cbStored = find(tabs[i], "cbStored");
      Object cbIndexed = find(tabs[i], "cbIndexed");
      Object cbTokenized = find(tabs[i], "cbTokenized");
      Object cbTVF = find(tabs[i], "cbTVF");
      Object cbTVFo = find(tabs[i], "cbTVFo");
      Object cbTVFp = find(tabs[i], "cbTVFp");
      Object cbCmp = find(tabs[i], "cbCmp");
      Object cbBin = find(tabs[i], "cbBin");
      Object cbONorms = find(tabs[i], "cbONorms");
      Object cbOTF = find(tabs[i], "cbOTF");
      String text = getString(fText, "text");
      byte[] binValue;
      boolean binary = getBoolean(cbBin, "selected");
      Field.TermVector tv;
      if (getBoolean(cbTVF, "selected")) {
        if (getBoolean(cbTVFo, "selected")) {
          if (getBoolean(cbTVFp, "selected")) {
            tv = Field.TermVector.WITH_POSITIONS_OFFSETS;
          } else {
            tv = Field.TermVector.WITH_OFFSETS;
          }
        } else {
          if (getBoolean(cbTVFp, "selected")) {
            tv = Field.TermVector.WITH_POSITIONS;
          } else {
            tv = Field.TermVector.YES;
          }
        }
      } else {
        tv = Field.TermVector.NO;
      }
      Field f;
      Store stored = getBoolean(cbStored, "selected") ? Field.Store.YES :
            Field.Store.NO;
      Index indexed = getBoolean(cbIndexed, "selected") ?
          (getBoolean(cbTokenized, "selected") ? Field.Index.ANALYZED : Field.Index.NOT_ANALYZED) :
            Field.Index.NO;
      if (stored.equals(Store.NO) && indexed.equals(Index.NO)) {
        errorMsg("Field '" + name + "' is neither stored nor indexed.");
        return false;
      }
      if (binary) {
        try {
          binValue = Util.hexToBytes(text);
        } catch (Throwable e) {
          errorMsg("Field '" + name + "': " + e.getMessage());
          return false;
        }
        f = new Field(name, binValue, stored);
      } else {
        f = new Field(name, text, stored, indexed, tv);
      }
      f.setOmitNorms(getBoolean(cbONorms, "selected"));
      f.setOmitTermFreqAndPositions(getBoolean(cbOTF, "selected"));
      String boostS = getString(fBoost, "text").trim();
      if (!boostS.equals("") && !boostS.equals("1.0")) {
        float boost = 1.0f;
        try {
          boost = Float.parseFloat(boostS);
        } catch (Exception e) {
          e.printStackTrace();
        }
        f.setBoost(boost);
      }
      doc.add(f);
    }
    IndexWriter writer = null;
    boolean res = false;
    String msg = null;
    try {
      ir.close();
      IndexDeletionPolicy policy;
      if (keepCommits) {
        policy = new KeepAllIndexDeletionPolicy();
      } else {
        policy = new KeepLastIndexDeletionPolicy();
      }
      writer = new IndexWriter(dir, a, false, policy, MaxFieldLength.UNLIMITED);
      writer.setUseCompoundFile(IndexGate.preferCompoundFormat(dir));
      writer.addDocument(doc);
      res = true;
    } catch (Exception e) {
      e.printStackTrace();
      msg = "FAILED: " + e.getMessage();
      res = false;
    } finally {
      try {
        if (writer != null) writer.close();
      } catch (Exception e) {
        e.printStackTrace();
        res = false;
        msg = "FAILED: " + e.getMessage();
      }
      remove(editdoc);
      try {
        actionReopen();
        // show Documents tab
        Object tabpane = find("maintpane");
        setInteger(tabpane, "selected", 1);
      } catch (Exception e) {
        e.printStackTrace();
        res = false;
        msg = "FAILED: " + e.getMessage();
      }
    }
    if (!res) {
      errorMsg(msg);
    }
    return res;
  }

  public void actionEditReplace(Object editdoc) {
    if (!actionEditAdd(editdoc)) return;
    Integer DocNum = (Integer) getProperty(editdoc, "docNum");
    if (DocNum == null) return;
    try {
      ir.deleteDocument(DocNum.intValue());
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg("ERROR deleting: " + e.getMessage());
      return;
    }
  }

  public void actionEditAddField(Object editdoc) {
    String name = getString(find(editdoc, "fNewName"), "text");
    if (name.trim().equals("")) {
      showStatus("FAILED: Field name is required.");
      return;
    }
    name = name.trim();
    Object editTabs = find(editdoc, "editTabs");
    Object tab = create("tab");
    setString(tab, "text", name);
    setFont(tab, getFont().deriveFont(Font.BOLD));
    add(editTabs, tab);
    addComponent(tab, "/xml/editfield.xml", null, null);
    repaint(editTabs);
  }

  public void actionEditDeleteField(Object editfield) {
    Object tab = getParent(editfield);
    remove(tab);
  }

  /** More Like this query from the current doc (or selected fields) */
  public void actionMLT(Object docNum, Object docTable) {
    if (ir == null) {
      errorMsg(MSG_NOINDEX);
      return;
    }
    int id = 0;
    try {
      id = Integer.parseInt(getString(docNum, "text"));
    } catch (NumberFormatException nfe) {
      errorMsg("Invalid document number");
      return;
    }
    MoreLikeThis mlt = new MoreLikeThis(ir, similarity != null ? similarity : new DefaultSimilarity());
    mlt.setFieldNames((String[])ir.getFieldNames(FieldOption.INDEXED).toArray(new String[0]));
    mlt.setMinTermFreq(1);
    mlt.setMaxQueryTerms(50);
    mlt.setAnalyzer(createAnalyzer(find("srchOptTabs")));
    Object[] rows = getSelectedItems(docTable);
    BooleanQuery similar = null;
    if (rows != null && rows.length > 0) {
      // collect text from fields
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < rows.length; i++) {
        Field f = (Field)getProperty(rows[i], "field");
        if (f == null) {
          continue;
        }
        String s = f.stringValue();
        if (s == null || s.trim().length() == 0) {
          continue;
        }
        if (sb.length() > 0) sb.append(" ");
        sb.append(s);
      }
      try {
        similar = (BooleanQuery)mlt.like(new StringReader(sb.toString()));
      } catch (Exception e) {
        e.printStackTrace();
        errorMsg("FAILED: " + e.getMessage());
        return;
      }
    } else {
      try {
        similar = (BooleanQuery)mlt.like(id);
      } catch (Exception e) {
        e.printStackTrace();
        errorMsg("FAILED: " + e.getMessage());
        return;
      }
    }
    if (similar.clauses() != null && similar.clauses().size() > 0) {
      //System.err.println("SIMILAR: " + similar);
      Object tabpane = find("maintpane");
      setInteger(tabpane, "selected", 2);
      Object qField = find("qField");
      setString(qField, "text", similar.toString());
    } else {
      showStatus("WARN: empty query - check Analyzer settings");
    }
  }
  
  private void _showDocFields(int docid, Document doc) {
    Object table = find("docTable");
    setString(find("docNum"), "text", String.valueOf(docid));
    removeAll(table);
    putProperty(table, "doc", doc);
    putProperty(table, "docNum", new Integer(docid));
    if (doc == null) {
      setString(find("docNum1"), "text", String.valueOf(docid) + "  DELETED");
      return;
    }
    setString(find("docNum1"), "text", String.valueOf(docid));
    for (int i = 0; i < idxFields.length; i++) {
      Field[] fields = doc.getFields(idxFields[i]);
      if (fields == null) {
        addFieldRow(table, idxFields[i], null, docid);
        continue;
      }
      for (int j = 0; j < fields.length; j++) {
//        if (fields[j].isBinary()) {
//          System.out.println("f.len=" + fields[j].getBinaryLength() + ", doc.len=" + doc.getBinaryValue(idxFields[i]).length);
//        }
        addFieldRow(table, idxFields[i], fields[j], docid);
      }
    }
    doLayout(table);
  }

  Font courier = null;
  private void addFieldRow(Object table, String fName, Field f, int docid) {
    Object row = create("row");
    add(table, row);
    putProperty(row, "field", f);
    putProperty(row, "fName", fName);
    Object cell = create("cell");
    setString(cell, "text", fName );
    add(row, cell);
    cell = create("cell");
    
    setString(cell, "text", Util.fieldFlags(f));
    if (f == null) {
      setBoolean(cell, "enabled", false);
    }
    setFont(cell, "font", courier);
    setChoice(cell, "alignment", "center");
    add(row, cell);
    cell = create("cell");
    if (f != null) {
      try {
        if (ir.hasNorms(fName)) {
          setString(cell, "text", String.valueOf(Similarity.decodeNorm(ir.norms(fName)[docid])));
        } else {
          setString(cell, "text", "---");
        }
      } catch (IOException ioe) {
        ioe.printStackTrace();
        setString(cell, "text", "!?!");
      }
    } else {
      setString(cell, "text", "---");
      setBoolean(cell, "enabled", false);
    }
    add(row, cell);
    cell = create("cell");
    if (f != null) {
      String text = f.stringValue();
      if (text == null && f.isBinary()) {
        text = Util.bytesToHex(f.getBinaryValue(), f.getBinaryOffset(),
            f.getBinaryLength(), false);
      }
      Decoder dec = decoders.get(f.name());
      if (dec == null) dec = defDecoder;
      try {
        if (f.isStored()) {
          text = dec.decodeStored(f.name(), text);
        } else {
          text = dec.decodeTerm(f.name(), text);
        }
      } catch (Throwable e) {
        setColor(cell, "foreground", Color.RED);
      }
      setString(cell, "text", Util.escape(text));
    } else {
      setString(cell, "text", "<not present or not stored>");
      setBoolean(cell, "enabled", false);
    }
    add(row, cell);
  }

  public void showTV(Object table) {
    final Object row = getSelectedItem(table);
    if (row == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    final Integer DocId = (Integer) getProperty(table, "docNum");
    if (DocId == null) {
      showStatus("Missing Doc. Id.");
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          String fName = (String) getProperty(row, "fName");
          TermFreqVector tfv = ir.getTermFreqVector(DocId.intValue(), fName);
          if (tfv == null) {
            showStatus("Term Vector not available.");
            return;
          }
          Object dialog = addComponent(null, "/xml/vector.xml", null, null);
          setString(find(dialog, "fld"), "text", fName);
          Object vTable = find(dialog, "vTable");
          IntPair[] tvs = new IntPair[tfv.size()];
          String[] terms = tfv.getTerms();
          int[] freqs = tfv.getTermFrequencies();
          TermPositionVector tpv = null;
          Decoder dec = decoders.get(fName);
          if (dec == null) dec = defDecoder;
          if (tfv instanceof TermPositionVector) tpv = (TermPositionVector)tfv;
          for (int i = 0; i < terms.length; i++) {
            IntPair ip = new IntPair(freqs[i], terms[i]);
            if (tpv != null) {
              ip.offsets = tpv.getOffsets(i);
              ip.positions = tpv.getTermPositions(i);
            }
            tvs[i] = ip;
          }
          
          Arrays.sort(tvs, new IntPair.PairComparator(false, true));
          for (int i = 0; i < tvs.length; i++) {
            Object r = create("row");
            add(vTable, r);
            putProperty(r, "tf", tvs[i]);
            Object cell = create("cell");
            String s;
            try {
              s = dec.decodeTerm(fName, tvs[i].text);
            } catch (Throwable e) {
              s = tvs[i].text;
              setColor(cell, "foreground", Color.RED);
            }
            setString(cell, "text", Util.escape(s));
            add(r, cell);
            cell = create("cell");
            setString(cell, "text", String.valueOf(tvs[i].cnt));
            add(r, cell);
            cell = create("cell");
            if (tvs[i].positions != null) {
              StringBuilder sb = new StringBuilder();
              for (int k = 0; k < tvs[i].positions.length; k++) {
                if (k > 0) sb.append(',');
                sb.append(String.valueOf(tvs[i].positions[k]));
              }
              setString(cell, "text", sb.toString());
            }
            add(r, cell);
            cell = create("cell");
            if (tvs[i].offsets != null) {
              StringBuilder sb = new StringBuilder();
              for (int k = 0; k < tvs[i].offsets.length; k++) {
                if (k > 0) sb.append(',');
                TermVectorOffsetInfo tvfi = tvs[i].offsets[k];
                sb.append(tvfi.getStartOffset() + "-" + tvfi.getEndOffset());
              }
              setString(cell, "text", sb.toString());
            }
            add(r, cell);
          }
          add(dialog);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }
  
  public void clipTV(Object vTable) {
    Object[] rows = getItems(vTable);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < rows.length; i++) {
      IntPair tf = (IntPair)getProperty(rows[i], "tf");
      sb.append(tf.cnt + "\t" + tf.text);
      if (tf.positions != null) {
        sb.append("\t");
        for (int k = 0; k < tf.positions.length; k++) {
          if (k > 0) sb.append(',');
          sb.append(String.valueOf(tf.positions[k]));
        }
      }
      if (tf.offsets != null) {
        if (tf.positions == null) sb.append("\t");
        sb.append("\t");
        for (int k = 0; k < tf.offsets.length; k++) {
          if (k > 0) sb.append(',');
          TermVectorOffsetInfo tvfi = tf.offsets[k];
          sb.append(tvfi.getStartOffset() + "-" + tvfi.getEndOffset());
        }
      }
      sb.append("\n");
    }
    StringSelection sel = new StringSelection(sb.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, this);
  }
  
  public void actionSetNorm(Object table) throws Exception {
    Object row = getSelectedItem(table);
    if (row == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    Field f = (Field) getProperty(row, "field");
    if (f == null) {
      showStatus("No data available for this field");
      return;
    }
    if (!f.isIndexed()) {
      showStatus("Cannot set norm value - this field is not indexed.");
      return;
    }
    Object dialog = addComponent(null, "/xml/fnorm.xml", null, null);
    Integer docNum = (Integer)getProperty(table, "docNum");
    putProperty(dialog, "docNum", getProperty(table, "docNum"));
    putProperty(dialog, "field", f);
    Object curNorm = find(dialog, "curNorm");
    Object newNorm = find(dialog, "newNorm");
    Object encNorm = find(dialog, "encNorm");
    Object doc = find(dialog, "docNum");
    Object fld = find(dialog, "fld");
    setString(doc, "text", String.valueOf(docNum.intValue()));
    setString(fld, "text", f.name());
    try {
      byte curBVal = ir.norms(f.name())[docNum.intValue()];
      float curFVal = Similarity.decodeNorm(curBVal);
      setString(curNorm, "text", String.valueOf(curFVal));
      setString(newNorm, "text", String.valueOf(curFVal));
      setString(encNorm, "text", String.valueOf(curFVal) +
          " (0x" + Util.byteToHex(curBVal) + ")");
    } catch (Exception e) {
      errorMsg("Error reading norm: " + e.toString());
      return;
    }
    add(dialog);
    displayNewNorm(dialog);
  }
  
  public void displayNewNorm(Object dialog) {
    Object newNorm = find(dialog, "newNorm");
    Object encNorm = find(dialog, "encNorm");
    try {
      float newFVal = Float.parseFloat(getString(newNorm, "text"));
      byte newBVal = Similarity.encodeNorm(newFVal);
      float encFVal = Similarity.decodeNorm(newBVal);
      setString(encNorm, "text", String.valueOf(encFVal) +
          " (0x" + Util.byteToHex(newBVal) + ")");
      putProperty(dialog, "newNorm", new Float(newFVal));
      doLayout(dialog);
    } catch (Exception e) {
      // XXX eat silently
    }
  }
  
  public void setNorm(Object dialog) {
    boolean singleDoc = getBoolean(find(dialog, "fDoc"), "selected");
    boolean allDoc = getBoolean(find(dialog, "fAll"), "selected");
    boolean ranges = getBoolean(find(dialog, "fList"), "selected");
    Float newFVal = (Float)getProperty(dialog, "newNorm");
    Integer docNum = (Integer)getProperty(dialog, "docNum");
    Field f = (Field)getProperty(dialog, "field");
    try {
      if (singleDoc) {
        ir.setNorm(docNum.intValue(), f.name(), newFVal.floatValue());
      } else if (allDoc) {
        for (int i = 0; i < ir.maxDoc(); i++) {
          if (ir.isDeleted(i)) continue;
          ir.setNorm(i, f.name(), newFVal.floatValue());
        }
      } else if (ranges) {
        String expr = getString(find(dialog, "frange"), "text");
        Ranges r = Ranges.parse(expr);
        if (r.cardinality() == 0) {
          infoMsg("Empty list - no documents selected, no modifications.");
        } else {
          DocIdSetIterator it = r.iterator();
          int docId;
          while ((docId = it.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
            if (ir.isDeleted(docId)) continue;
            ir.setNorm(docId, f.name(), newFVal.floatValue());
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg("Set norms failed: " + e.toString());
    }
    remove(dialog);
    Object table = find("docTable");
    Document doc = (Document)getProperty(table, "doc");
    _showDocFields(docNum.intValue(), doc);
  }
  
  public void showTField(Object table) {
    Object row = getSelectedItem(table);
    if (row == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    Field f = (Field) getProperty(row, "field");
    if (f == null) {
      showStatus("No data available for this field");
      return;
    }
    Object dialog = addComponent(null, "/xml/field.xml", null, null);
    Object fName = find(dialog, "fld");
    putProperty(dialog, "f", f);
    setString(fName, "text", f.name());
    add(dialog);
    _showData(dialog);
  }
  
  public void _showData(Object dialog) {
    Object fDataText = find(dialog, "fDataText");
    Field f = (Field)getProperty(dialog, "f");
    String value = null;
    String enc = "cbUtf";
    Object choice = getSelectedItem(find(dialog, "cbData"));
    String selEnc = getString(choice, "name");
    boolean warn = false;
    if (selEnc != null) enc = selEnc;
    int len = 0;
    byte[] data = null;
    if (f.isBinary()) {
      data = new byte[f.getBinaryLength()];
      System.arraycopy(f.getBinaryValue(), f.getBinaryOffset(), data, 0,
          f.getBinaryLength());
    }
    else {
      try {
        data = f.stringValue().getBytes("UTF-8");
      } catch (UnsupportedEncodingException uee) {
        warn = true;
        uee.printStackTrace();
        data = f.stringValue().getBytes();
      }
    }
    if (data == null) data = new byte[0];
    if (enc.equals("cbHex")) {
      setString(find(dialog, "unit"), "text", " bytes");
      value = Util.bytesToHex(data, 0, data.length, true);
      len = data.length;
    } else if (enc.equals("cbUtf")) {
      setString(find(dialog, "unit"), "text", " UTF-8 characters");
      value = f.stringValue();
      if (value != null) len = value.length();
    } else if (enc.equals("cbDef")) {
      setString(find(dialog, "unit"), "text", " characters");
      value = new String(data);
      len = value.length();
    } else if (enc.equals("cbDate")) {
      try {
        Date d = DateTools.stringToDate(f.stringValue());
        value = d.toString();
        len = 1;
      } catch (Exception e) {
        warn = true;
        value = Util.bytesToHex(data, 0, data.length, true);
      }
    } else if (enc.equals("cbNum")) {
      try {
        long num = NumericUtils.prefixCodedToLong(f.stringValue());
        value = String.valueOf(num);
        len = 1;
      } catch (Exception e) {
        warn = true;
        value = Util.bytesToHex(data, 0, data.length, true);
      }
    } else if (enc.equals("cbInt")) {
      if (data.length % 4 == 0) {
        setString(find(dialog, "unit"), "text", " int values");
        len = data.length / 4;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < data.length; k += 4) {
          if (k > 0) sb.append(',');
          sb.append(String.valueOf(PayloadHelper.decodeInt(data, k)));
        }
        value = sb.toString();
      } else {
        warn = true;
        value = Util.bytesToHex(data, 0, data.length, true);
      }
    } else if (enc.equals("cbFloat")) {
      if (data.length % 4 == 0) {
        setString(find(dialog, "unit"), "text", " float values");
        len = data.length / 4;
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < data.length; k += 4) {
          if (k > 0) sb.append(',');
          sb.append(String.valueOf(PayloadHelper.decodeFloat(data, k)));
        }
        value = sb.toString();
      } else {
        warn = true;
        value = Util.bytesToHex(data, 0, data.length, true);
      }
    }
    setString(fDataText, "text", value);
    setString(find(dialog, "len"), "text", String.valueOf(len));
    if (warn) {
      setBoolean(fDataText, "enabled", false);
      errorMsg(MSG_CONV_ERROR);
    } else {
      setBoolean(fDataText, "enabled", true);
    }
  }
  
  public void saveField(Object table) {
    Object row = getSelectedItem(table);
    if (row == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    Field f = (Field) getProperty(row, "field");
    if (f == null) {
      showStatus("No data available for this field");
      return;
    }
    JFileChooser fd = new JFileChooser();
    fd.setDialogType(JFileChooser.SAVE_DIALOG);
    fd.setDialogTitle("Save field content to a file");
    fd.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fd.setFileHidingEnabled(false);
    if (this.baseDir != null)
      fd.setCurrentDirectory(new File(this.baseDir));
    else fd.setCurrentDirectory(new File(System.getProperty("user.dir")));
    int res = fd.showSaveDialog(this);
    File iFile = null;
    if (res == JFileChooser.APPROVE_OPTION) iFile = fd.getSelectedFile();
    if (iFile == null) return;
    if (iFile.exists() && iFile.isDirectory()) {
      errorMsg("Can't overwrite a directory.");
      return;
    }
    Object progress = null;
    try {
      byte[] data = null;
      if (f.isBinary()) data = f.getBinaryValue();
      else {
        try {
          data = f.stringValue().getBytes("UTF-8");
        } catch (UnsupportedEncodingException uee) {
          uee.printStackTrace();
          errorMsg(uee.toString());
          data = f.stringValue().getBytes();
        }
      }
      if (data == null || data.length == 0) {
        showStatus("No data available");
        return;
      }
      progress = addComponent(null, "/xml/progress.xml", null, null);
      setString(find(progress, "msg"), "text", "Saving...");
      Object bar = find(progress, "bar");
      setInteger(bar, "maximum", 100);
      OutputStream os = new FileOutputStream(iFile);
      int delta = data.length / 100;
      if (delta == 0) delta = 1;
      add(progress);
      for (int i = 0; i < data.length; i++) {
        os.write(data[i]);
        if (i % delta == 0) {
          setInteger(bar, "value", i / delta);
        }
      }
      os.flush();
      os.close();
      setString(find(progress, "msg"), "text", "Done!");
      try { Thread.sleep(1000); } catch (Exception e) {};
    } catch (IOException ioe) {
      ioe.printStackTrace();
      errorMsg("Can't save: " + ioe);
      return;
    } finally {
      if (progress != null) remove(progress);
    }
  }

  public void clipCopyFields(Object table) {
    Object[] rows = getSelectedItems(table);
    if (rows == null || rows.length == 0) return;
    Document doc = (Document) getProperty(table, "doc");
    if (doc == null) return;
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < rows.length; i++) {
      Field f = (Field) getProperty(rows[i], "field");
      if (f == null) continue;
      if (i > 0) sb.append('\n');
      sb.append(f.toString());
    }
    StringSelection sel = new StringSelection(sb.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, this);
  }

  public void clipCopyDoc(Object table) {
    Document doc = (Document) getProperty(table, "doc");
    if (doc == null) return;
    StringBuffer sb = new StringBuffer();
    Object[] rows = getItems(table);
    for (int i = 0; i < rows.length; i++) {
      Field f = (Field) getProperty(rows[i], "field");
      if (f == null) continue;
      if (i > 0) sb.append('\n');
      sb.append(f.toString());
    }
    StringSelection sel = new StringSelection(sb.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, this);
  }

  public void showFirstTerm(final Object fCombo, final Object fText) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          String fld = getString(fCombo, "text");
          TermEnum te = ir.terms(new Term(fld, ""));
          Term t = te.term();
          _showTerm(fCombo, fText, t);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  public void showNextTerm(final Object fCombo, final Object fText) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          String text;
          Term rawTerm = (Term)getProperty(fText, "term");
          text = getString(fText, "text");
          if (rawTerm != null) {
            String s = (String)getProperty(fText, "decText");
            if (s.equals(text)) {
              text = rawTerm.text();
            }
          }
          String fld = getString(fCombo, "text");
          TermEnum te = null;
          if (text == null || text.trim().equals("")) text = "";
          te = ir.terms(new Term(fld, text));
          te.next();
          Term t = te.term();
          _showTerm(fCombo, fText, t);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  public void showTerm(final Object fCombo, final Object fText) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          String text;
          Term rawTerm = (Term)getProperty(fText, "term");
          text = getString(fText, "text");
          if (rawTerm != null) {
            String s = (String)getProperty(fText, "decText");
            if (s.equals(text)) {
              text = rawTerm.text();
            }
          }
          String fld = getString(fCombo, "text");
          if (text == null || text.trim().equals("")) return;
          Term t = new Term(fld, text);
          if (ir.docFreq(t) == 0) { // missing term
            TermEnum te = ir.terms(t);
            t = te.term();
          }
          _showTerm(fCombo, fText, t);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  private void _showTerm(Object fCombo, Object fText, final Term t) {
    if (t == null) {
      showStatus("No terms?!");
      return;
    }
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    Object[] choices = getItems(fCombo);
    for (int i = 0; i < choices.length; i++) {
      if (t.field().equals(getString(choices[i], "text"))) {
        setInteger(fCombo, "selected", i);
        break;
      }
    }
    Decoder dec = decoders.get(t.field());
    if (dec == null) dec = defDecoder;
    String s = null;
    boolean decodeErr = false;
    try {
      s = dec.decodeTerm(t.field(), t.text());
    } catch (Throwable e) {
      s = e.getMessage();
      decodeErr = true;
    }
    setString(fText, "text", t.text());
    Object rawText = find("decText");
    if (!s.equals(t.text())) {
      setString(rawText, "text", s);
      if (decodeErr) {
        setColor(rawText, "foreground", Color.RED);
      } else {
        setColor(rawText, "foreground", Color.BLUE);        
      }
    } else {
      setString(rawText, "text", "");
      setColor(rawText, "foreground", Color.BLACK);
    }
    putProperty(fText, "term", t);
    putProperty(fText, "decText", s);
    putProperty(fText, "td", null);
    setString(find("tdNum"), "text", "?");
    setString(find("tFreq"), "text", "?");
    SlowThread st = new SlowThread(this) {
      public void execute() {
        Object dFreq = find("dFreq");
        try {
          int freq = ir.docFreq(t);
          setString(dFreq, "text", String.valueOf(freq));
          dFreq = find("tdMax");
          setString(dFreq, "text", String.valueOf(freq));
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
          setString(dFreq, "text", "?");
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  public void showFirstTermDoc(final Object fText) {
    final Term t = (Term) getProperty(fText, "term");
    if (t == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          TermPositions td = ir.termPositions(t);
          td.next();
          setString(find("tdNum"), "text", "1");
          putProperty(fText, "td", td);
          _showTermDoc(fText, td);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  public void showNextTermDoc(final Object fText) {
    final Term t = (Term) getProperty(fText, "term");
    if (t == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          TermPositions td = (TermPositions) getProperty(fText, "td");
          if (td == null) {
            showFirstTermDoc(fText);
            return;
          }
          if (!td.next()) return;
          Object tdNum = find("tdNum");
          String sCnt = getString(tdNum, "text");
          int cnt = 1;
          try {
            cnt = Integer.parseInt(sCnt);
          } catch (Exception e) {}
          ;
          setString(tdNum, "text", String.valueOf(cnt + 1));
          _showTermDoc(fText, td);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }
  
  public void showPositions(final Object fText) {
    final Term t = (Term) getProperty(fText, "term");
    if (t == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          TermPositions td = (TermPositions) getProperty(fText, "td");
          if (td == null) {
            return;
          }
          Object dialog = addComponent(null, "/xml/positions.xml", null, null);
          setString(find(dialog, "term"), "text", t.toString());
          String docNum = getString(find("docNum"), "text");
          setString(find(dialog, "docNum"), "text", docNum);
          setString(find(dialog, "freq"), "text", String.valueOf(td.freq()));
          putProperty(dialog, "td", td);
          Object pTable = find(dialog, "pTable");
          removeAll(pTable);
          int freq = td.freq();
          for (int i = 0; i < freq; i++) {
            try {
              int pos = td.nextPosition();
              Object r = create("row");
              Object cell = create("cell");
              setString(cell, "text", String.valueOf(pos));
              add(r, cell);
              cell = create("cell");
              add(r, cell);
              if (td.isPayloadAvailable()) {
                byte[] payload = new byte[td.getPayloadLength()];
                td.getPayload(payload, 0);
                putProperty(r, "payload", payload);
              }
              add(pTable, r);
            } catch (IOException ioe) {
              errorMsg("Error: " + ioe.toString());
              return;
            }
          }
          _showPayloads(dialog);
          add(dialog);
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }
  
  public void _showPayloads(Object dialog) {
    Object cbPay = find(dialog, "cbPay");
    Object choice = getSelectedItem(cbPay);
    String enc = "cbUtf";
    if (choice != null) enc = getString(choice, "name");
    Object pTable = find(dialog, "pTable");
    Object[] rows = getItems(pTable);
    boolean warn = false;
    for (int i = 0; i < rows.length; i++) {
      byte[] payload = (byte[])getProperty(rows[i], "payload");
      if (payload == null) continue;
      Object cell = getItem(rows[i], 1);
      String curEnc = enc;
      if (enc.equals("cbInt") || enc.equals("cbFloat")) {
        if (payload.length % 4 != 0)
          curEnc = "cbHex";
      }
      String val = "?";
      if (curEnc.equals("cbUtf")) {
        try {
          val = new String(payload, "UTF-8");
        } catch (Exception e) {
          e.printStackTrace();
          val = new String(payload);
          curEnc = "cbDef";
        }
      } else if (curEnc.equals("cbHex")) {
        val = Util.bytesToHex(payload, 0, payload.length, false);
      } else if (curEnc.equals("cbDef")) {
        val = new String(payload);
      } else if (curEnc.equals("cbInt")) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < payload.length; k += 4) {
          if (k > 0) sb.append(',');
          sb.append(String.valueOf(PayloadHelper.decodeInt(payload, k)));
        }
        val = sb.toString();
      } else if (curEnc.equals("cbFloat")) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < payload.length; k += 4) {
          if (k > 0) sb.append(',');
          sb.append(String.valueOf(PayloadHelper.decodeFloat(payload, k)));
        }
        val = sb.toString();
      }
      setString(cell, "text", val);
      if (!curEnc.equals(enc)) {
        setBoolean(cell, "enabled", false);
        warn = true;
      } else {
        setBoolean(cell, "enabled", true);
      }
    }
    if (warn) {
      errorMsg(MSG_CONV_ERROR);
    }
  }
  
  public void clipPositions(Object pTable) {
    Object[] rows = getItems(pTable);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < rows.length; i++) {
      if (i > 0) sb.append('\n');
      Object[] cells = getItems(rows[i]);
      for (int k = 0; k < cells.length; k++) {
        if (k > 0) sb.append('\t');
        sb.append(getString(cells[k], "text"));
      }
    }
    StringSelection sel = new StringSelection(sb.toString());
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, this);
  }

  public void showAllTermDoc(Object fText) {
    Term t = (Term) getProperty(fText, "term");
    if (t == null) return;
    if (ir == null) {
      showStatus("MSG_NOINDEX");
      return;
    }
    Object tabpane = find("maintpane");
    setInteger(tabpane, "selected", 2);
    Object qField = find("qField");
    setString(qField, "text", t.field() + ":" + t.text());
    Object qFieldParsed = find("qFieldParsed");
    Object ckScoreRes = find("ckScoreRes");
    Object ckOrderRes = find("ckOrderRes");
    Object cntRepeat = find("cntRepeat");
    final boolean scoreRes = getBoolean(ckScoreRes, "selected");
    final boolean orderRes = getBoolean(ckOrderRes, "selected");
    final int repeat = Integer.parseInt(getString(cntRepeat, "text"));
    final Query q = new TermQuery(t);
    setString(qFieldParsed, "text", q.toString());
    SlowThread st = new SlowThread(this) {
      public void execute() {
        IndexSearcher is = null;
        try {
          is = new IndexSearcher(ir);
          Object sTable = find("sTable");
          removeAll(sTable);
          AllHitsCollector ahc = new AllHitsCollector(orderRes, scoreRes);
          _search(q, is, ahc, sTable, repeat);
        } catch (Exception e) {
          e.printStackTrace();
          errorMsg(e.getMessage());
        } finally {
          if (is != null) try {
            is.close();
          } catch (Exception e1) {}
          ;
        }        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }
  
  public Analyzer createAnalyzer(Object srchOpts) {
    Analyzer res = null;
    String sAType = getString(find(srchOpts, "cbType"), "text");
    if (sAType.trim().equals("")) {
      sAType = "org.apache.lucene.analysis.standard.StandardAnalyzer";
      setString(find("cbType"), "text", sAType);
    }
    String arg = getString(find(srchOpts, "snoName"), "text");
    if (arg == null) arg = "";
    try {
      Constructor zeroArg = null, zeroArgV = null, oneArg = null, oneArgV = null;
      try {
        zeroArgV = Class.forName(sAType).getConstructor(new Class[]{Version.class});
      } catch (NoSuchMethodException e) {
        zeroArgV = null;
        try {
          zeroArg = Class.forName(sAType).getConstructor(new Class[0]);
        } catch (NoSuchMethodException e1) {
          zeroArg = null;
        }
      }
      try {
        oneArgV = Class.forName(sAType).getConstructor(new Class[]{Version.class, String.class});
      } catch (NoSuchMethodException e) {
        oneArgV = null;
        try {
          oneArg = Class.forName(sAType).getConstructor(new Class[]{String.class});
        } catch (NoSuchMethodException e1) {
          oneArg = null;
        }
      }
      if (arg.length() == 0) {
        if (zeroArgV != null) {
          res = (Analyzer)zeroArgV.newInstance(Version.LUCENE_CURRENT);
        } else if (zeroArg != null) {
          res = (Analyzer)zeroArg.newInstance();
        } else if (oneArgV != null) {
          res = (Analyzer)oneArgV.newInstance(new Object[]{Version.LUCENE_CURRENT, arg});
        } else if (oneArg != null) {
          res = (Analyzer)oneArg.newInstance(new Object[]{arg});
        } else {
          throw new Exception("Must have a zero-arg or (Version) or (Version, String) constructor");
        }
      } else {
        if (oneArgV != null) {
          res = (Analyzer)oneArgV.newInstance(new Object[]{Version.LUCENE_CURRENT, arg});
        } else if (oneArg != null) {
          res = (Analyzer)oneArg.newInstance(new Object[]{arg});
        } else if (zeroArgV != null) {
          res = (Analyzer)zeroArgV.newInstance(new Object[]{Version.LUCENE_CURRENT});
        } else if (zeroArg != null) {
          res = (Analyzer)zeroArg.newInstance(new Object[0]);
        } else {
          throw new Exception("Must have a zero-arg or (String) constructor");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      errorMsg("Analyzer '" + sAType + "' error: " + e.getMessage() + ". Using StandardAnalyzer.");
      res = stdAnalyzer;
    }
    Prefs.setProperty(Prefs.P_ANALYZER, res.getClass().getName());
    return res;
  }
  
  protected String getDefaultField(Object srchOptTabs) {
    String defField = getString(find(srchOptTabs, "defFld"), "text");
    if (defField == null || defField.trim().equals("")) {
      if (ir != null) {
        defField = idxFields[0];
        setString(find(srchOptTabs, "defFld"), "text", defField);
      } else {
        defField = "DEFAULT";
      }
    }
    return defField;
  }

  /**
   * Create a Query instance that corresponds to values selected in the UI,
   * such as analyzer class name and arguments, and default field.
   * @return
   */
  public Query createQuery(String queryString) throws Exception {
    Object srchOpts = find("srchOptTabs");
    analyzer = createAnalyzer(srchOpts);
    String defField = getDefaultField(srchOpts);
    QueryParser qp = new QueryParser(Version.LUCENE_CURRENT, defField, analyzer);
    Object ckXmlParser = find(srchOpts, "ckXmlParser");
    Object ckWild = find(srchOpts, "ckWild");
    Object ckPosIncr = find(srchOpts, "ckPosIncr");
    Object ckLoExp = find(srchOpts, "ckLoExp");
    Object cbDateRes = find(srchOpts, "cbDateRes");
    DateTools.Resolution resolution = Util.getResolution(getString(cbDateRes, "text"));
    Object cbOp = find(srchOpts, "cbOp");
    Object bqMaxCount = find(srchOpts, "bqMaxCount");
    int maxCount = 1024;
    try {
      maxCount = Integer.parseInt(getString(bqMaxCount, "text"));
    } catch (Exception e) {
      e.printStackTrace();
      showStatus("Invalid BooleanQuery max clause count, using default 1024");
    }
    QueryParser.Operator op;
    BooleanQuery.setMaxClauseCount(maxCount);
    String opString = getString(cbOp, "text");
    if (opString.equalsIgnoreCase("OR")) {
      op = QueryParser.OR_OPERATOR;
    } else {
      op = QueryParser.AND_OPERATOR;
    }
    qp.setAllowLeadingWildcard(getBoolean(ckWild, "selected"));
    qp.setEnablePositionIncrements(getBoolean(ckPosIncr, "selected"));
    qp.setLowercaseExpandedTerms(getBoolean(ckLoExp, "selected"));
    qp.setDateResolution(resolution);
    qp.setDefaultOperator(op);
    if (getBoolean(ckXmlParser, "selected")) {
      
      CoreParser cp = createParser(defField,analyzer);
      Query q = cp.parse(new ByteArrayInputStream(queryString.getBytes("UTF-8")));
      return q;
    } else {
      return qp.parse(queryString);
    }
  }
  
  private CoreParser createParser(String defaultField, Analyzer analyzer ) throws Exception
  {
	if(xmlQueryParserFactoryClassName==null)
	{
		//Use the default
		return  new CorePlusExtensionsParser(defaultField,analyzer);
	}
	//Use a user-defined parser (classname passed in -xmlQueryParserFactory command-line parameter
	XmlQueryParserFactory parserFactory=(XmlQueryParserFactory) Class.forName(xmlQueryParserFactoryClassName).newInstance();
	return parserFactory.createParser(defaultField,analyzer);
  }

public Similarity createSimilarity(Object srchOpts) {
    Object ckSimDef = find(srchOpts, "ckSimDef");
    Object ckSimSweet = find(srchOpts, "ckSimSweet");
    Object ckSimOther = find(srchOpts, "ckSimOther");
    Object simClass = find(srchOpts, "simClass");
    Object ckSimCust = find(srchOpts, "ckSimCust");
    if (getBoolean(ckSimDef, "selected")) {
      return new DefaultSimilarity();
    } else if (getBoolean(ckSimSweet, "selected")) {
      return new SweetSpotSimilarity();
    } else if (getBoolean(ckSimOther, "selected")) {
      try {
        Class clazz = Class.forName(getString(simClass, "text"));
        if (Similarity.class.isAssignableFrom(clazz)) {
          Similarity sim = (Similarity)clazz.newInstance();
          return sim;
        } else {
          throw new Exception("Not a subclass of Similarity: " + clazz.getName());
        }
      } catch (Exception e) {
        e.printStackTrace();
        showStatus("ERROR: invalid Similarity, using default");
        setBoolean(ckSimDef, "selected", true);
        setBoolean(ckSimOther, "selected", false);
        return new DefaultSimilarity();
      }
    } else if (getBoolean(ckSimCust, "selected")) {
      return similarity;
    } else {
      return new DefaultSimilarity();
    }
  }
  
  public AccessibleHitCollector createCollector(Object srchOpts) throws Exception {
    Object ckNormRes = find(srchOpts, "ckNormRes");
    Object ckAllRes = find(srchOpts, "ckAllRes");
    Object ckLimRes = find(srchOpts, "ckLimRes");
    Object ckLimTime = find(srchOpts, "ckLimTime");
    Object limTime = find(srchOpts, "limTime");
    Object ckLimCount = find(srchOpts, "ckLimCount");
    Object limCount = find(srchOpts, "limCount");
    Object ckScoreRes = find(srchOpts, "ckScoreRes");
    Object ckOrderRes = find(srchOpts, "ckOrderRes");
    boolean scoreRes = getBoolean(ckScoreRes, "selected");
    boolean orderRes = getBoolean(ckOrderRes, "selected");
    Collector hc = null;
    if (getBoolean(ckNormRes, "selected")) {
      return new AccessibleTopHitCollector(1000, orderRes, scoreRes);
    } else if (getBoolean(ckAllRes, "selected")) {
      return new AllHitsCollector(orderRes, scoreRes);
    } else if (getBoolean(ckLimRes, "selected")) {
      // figure out the type
      if (getBoolean(ckLimCount, "selected")) {
        int lim = Integer.parseInt(getString(limCount, "text"));
        return new CountLimitedHitCollector(lim, orderRes, scoreRes);
      } else if (getBoolean(ckLimTime, "selected")) {
        int lim = Integer.parseInt(getString(limTime, "text"));
        return new IntervalLimitedCollector(lim, orderRes, scoreRes);
      } else {
        throw new Exception("Unknown LimitedHitCollector type");
      }
    } else {
      throw new Exception("Unknown HitCollector type");
    }
  }

  public void explainStructure(Object qTabs) {
    Object qField = find("qField");
    String queryS = getString(qField, "text");
    if (queryS.trim().equals("")) {
      showStatus("Empty query");
      return;
    }
    showParsed();
    int idx = getSelectedIndex(qTabs);
    Query q = null;
    if (idx == 0) {
      q = (Query)getProperty(qField, "qParsed");
    } else {
      q = (Query)getProperty(qField, "qRewritten");
    }
    Object dialog = addComponent(this, "/xml/qexplain.xml", null, null);
    Object tree = find(dialog, "qTree");
    _explainStructure(tree, q);
  }
  
  private void _explainStructure(Object parent, Query q) {
    String clazz = q.getClass().getSimpleName();
    float boost = q.getBoost();
    Object n = create("node");
    add(parent, n);
    String msg = clazz;
    if (boost != 1.0f) {
      msg += ": boost=" + df.format(boost);
    }
    setFont(n, getFont().deriveFont(Font.BOLD));
    setString(n, "text", msg);
    if (clazz.equals("TermQuery")) {
      Object n1 = create("node");
      Term t = ((TermQuery)q).getTerm();
      setString(n1, "text", "Term: field='" + t.field() + "' text='" + t.text() + "'");
      add(n, n1);
    } else if (clazz.equals("BooleanQuery")) {
      BooleanQuery bq = (BooleanQuery)q;
      BooleanClause[] clauses = bq.getClauses();
      int max = bq.getMaxClauseCount();
      Object n1 = create("node");
      String descr = "clauses=" + clauses.length +
      ", maxClauses=" + max;
      if (bq.isCoordDisabled()) {
        descr += ", coord=false";
      }
      if (bq.getMinimumNumberShouldMatch() > 0) {
        descr += ", minShouldMatch=" + bq.getMinimumNumberShouldMatch();
      }
      setString(n1, "text", descr);
      add(n, n1);
      for (int i = 0; i < clauses.length; i++) {
        n1 = create("node");
        String occur;
        Occur occ = clauses[i].getOccur();
        if (occ.equals(Occur.MUST)) {
          occur = "MUST";
        } else if (occ.equals(Occur.MUST_NOT)) {
          occur = "MUST_NOT";
        } else if (occ.equals(Occur.SHOULD)) {
          occur = "SHOULD";
        } else {
          occur = occ.toString();
        }
        setString(n1, "text", "Clause " + i + ": " + occur);
        add(n, n1);
        _explainStructure(n1, clauses[i].getQuery());
      }
    } else if (clazz.equals("PrefixQuery")) {
      Object n1 = create("node");
      Term t = ((PrefixQuery)q).getPrefix();
      setString(n1, "text", "Prefix: field='" + t.field() + "' text='" + t.text() + "'");
      add(n, n1);
    } else if (clazz.equals("PhraseQuery")) {
      PhraseQuery pq = (PhraseQuery)q;
      setString(n, "text", getString(n, "text") + ", slop=" + pq.getSlop());
      int[] pos = pq.getPositions();
      Term[] terms = pq.getTerms();
      Object n1 = create("node");
      StringBuffer sb = new StringBuffer("pos: [");
      for (int i = 0; i < pos.length; i++) {
        if (i > 0) sb.append(',');
        sb.append("" + pos[i]);
      }
      sb.append("]");
      setString(n1, "text", sb.toString());
      add(n, n1);
      for (int i = 0; i < terms.length; i++) {
        n1 = create("node");
        setString(n1, "text", "Term " + i + ": field='" + terms[i].field() +
                "' text='" + terms[i].text() + "'");
        add(n, n1);
      }
    } else if (clazz.equals("MultiPhraseQuery")) {
      MultiPhraseQuery pq = (MultiPhraseQuery)q;
      setString(n, "text", getString(n, "text") + ", slop=" + pq.getSlop());
      int[] pos = pq.getPositions();
      Object n1 = create("node");
      StringBuffer sb = new StringBuffer("pos: [");
      for (int i = 0; i < pos.length; i++) {
        if (i > 0) sb.append(',');
        sb.append("" + pos[i]);
      }
      sb.append("]");
      setString(n1, "text", sb.toString());
      add(n, n1);
      n1 = create("node");
      System.err.println("MultiPhraseQuery is missing the public getTermArrays() :-(");
      setString(n1, "text", "toString: " + pq.toString());
      add(n, n1);
    } else if (clazz.equals("FuzzyQuery")) {
      FuzzyQuery fq = (FuzzyQuery)q;
      Object n1 = create("node");
      setString(n1, "text", "prefixLen=" + fq.getPrefixLength() +
              ", minSimilarity=" + df.format(fq.getMinSimilarity()));
      add(n, n1);
      // do some tricks with reflection...
      try {
        Method m = FuzzyQuery.class.getDeclaredMethod("getEnum", new Class[]{IndexReader.class});
        m.setAccessible(true);
        FilteredTermEnum fte = (FilteredTermEnum)m.invoke(fq, new Object[]{ir});
        n1 = create("node");
        String clz = fte.getClass().getName();
        setString(n1, "text", clz + ": diff=" + df.format(fte.difference()));
        add(n, n1);
        do {
          n1 = create("node");
          Term t = fte.term();
          setString(n1, "text", "Term: field='" + t.field() +
                  "' text='" + t.text() + "', docFreq=" + fte.docFreq());
          add(n, n1);
        } while (fte.next());
      } catch (Exception e) {
        n1 = create("node");
        setString(n1, "text", "FilteredTermEnum: Exception " + e.getMessage());
        add(n, n1);
      }
    } else if (clazz.equals("WildcardQuery")) {
      WildcardQuery wq = (WildcardQuery)q;
      Term t = wq.getTerm();
      setString(n, "text", getString(n, "text") + ", term=" + t);
      // do some tricks with reflection...
      try {
        Method m = WildcardQuery.class.getDeclaredMethod("getEnum", new Class[]{IndexReader.class});
        m.setAccessible(true);
        FilteredTermEnum fte = (FilteredTermEnum)m.invoke(wq, new Object[]{ir});
        Object n1 = create("node");
        String clz = fte.getClass().getName();
        setString(n1, "text", clz + ": diff=" + df.format(fte.difference()));
        add(n, n1);
        do {
          n1 = create("node");
          t = fte.term();
          if (t == null) continue;
          setString(n1, "text", "Term: field='" + t.field() +
                  "' text='" + t.text() + "', docFreq=" + fte.docFreq());
          add(n, n1);
        } while (fte.next());
      } catch (Exception e) {
        Object n1 = create("node");
        setString(n1, "text", "FilteredTermEnum: Exception " + e.getMessage());
        add(n, n1);
      }
    } else if (clazz.equals("TermRangeQuery")) {
      TermRangeQuery rq = (TermRangeQuery)q;
      setString(n, "text", getString(n, "text") + ", inclLower=" + rq.includesLower() + ", inclUpper=" + rq.includesUpper());
      Object n1 = create("node");
      setString(n1, "text", "lowerTerm=" + rq.getField() + ":" + rq.getLowerTerm() + "'");
      add(n, n1);
      n1 = create("node");
      setString(n1, "text", "upperTerm=" + rq.getField() + ":" + rq.getUpperTerm() + "'");
      add(n, n1);
    } else if (q instanceof FilteredQuery) {
      FilteredQuery fq = (FilteredQuery)q;
      Object n1 = create("node");
      setString(n1, "text", "Filter: " + fq.getFilter().toString());
      add(n, n1);
      _explainStructure(n, fq.getQuery());
    } else if (q instanceof SpanQuery) {
      SpanQuery sq = (SpanQuery)q;
      Class sqlass = sq.getClass();
      setString(n, "text", getString(n, "text") + ", field=" + sq.getField());
      if (sqlass == SpanOrQuery.class) {
        SpanOrQuery soq = (SpanOrQuery)sq;
        setString(n, "text", getString(n, "text") + ", " + soq.getClauses().length + " clauses");
        for (SpanQuery sq1 : soq.getClauses()) {
          _explainStructure(n, sq1);
        }
      } else if (sqlass == SpanFirstQuery.class) {
        SpanFirstQuery sfq = (SpanFirstQuery)sq;
        setString(n, "text", getString(n, "text") + ", end=" + sfq.getEnd() + ", match:");
        _explainStructure(n, sfq.getMatch());
      } else if (q instanceof SpanNearQuery) { // catch also known subclasses
        SpanNearQuery snq = (SpanNearQuery)sq;
        setString(n, "text", getString(n, "text") + ", slop=" + snq.getSlop());
        if (snq instanceof PayloadNearQuery) {
          try {
            java.lang.reflect.Field function = PayloadNearQuery.class.getDeclaredField("function");
            function.setAccessible(true);
            Object func = function.get(snq);
            setString(n, "text", getString(n, "text") + ", func=" + func.getClass().getSimpleName());
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        for (SpanQuery sq1 : snq.getClauses()) {
          _explainStructure(n, sq1);
        }
      } else if (sqlass == SpanNotQuery.class) {
        SpanNotQuery snq = (SpanNotQuery)sq;
        Object n1 = create("node");
        add(n, n1);
        setString(n1, "text", "Include:");
        _explainStructure(n1, snq.getInclude());
        n1 = create("node");
        add(n, n1);
        setString(n1, "text", "Exclude:");
        _explainStructure(n1, snq.getExclude());
      } else if (q instanceof SpanTermQuery) {
        SpanTermQuery stq = (SpanTermQuery)sq;
        setString(n, "text", getString(n, "text") + ", term=" + stq.getTerm());        
        if (stq instanceof PayloadTermQuery) {
          try {
            java.lang.reflect.Field function = PayloadTermQuery.class.getDeclaredField("function");
            function.setAccessible(true);
            Object func = function.get(stq);
            setString(n, "text", getString(n, "text") + ", func=" + func.getClass().getSimpleName());
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      } else {
        String defField = getDefaultField(find("srchOptTabs"));
        setString(n, "text", "class=" + q.getClass().getName() + ", " + getString(n, "text") + ", toString=" + q.toString(defField));
        HashSet<Term> terms = new HashSet<Term>();
        sq.extractTerms(terms);
        Object n1 = null;
        if (terms != null) {
          n1 = create("node");
          setString(n1, "text", "Matched terms (" + terms.size() + "):");
          add(n, n1);
          Iterator<Term> it = terms.iterator();
          while(it.hasNext()) {
            Object n2 = create("node");
            Term t = it.next();
            setString(n2, "text", "field='" + t.field() + "' text='" + t.text() + "'");
            add(n1, n2);
          }
        } else {
          n1 = create("node");
          setString(n1, "text", "<no terms matched>");
          add(n, n1);
        }
      }
      if (ir != null) {
        Object n1 = null;
        try {
          Spans spans = sq.getSpans(ir);
          if (spans != null) {
            n1 = create("node");
            int cnt = 0;
            while (spans.next()) {
              Object n2 = create("node");
              setString(n2, "text", "doc=" + spans.doc() +
                      ", start=" + spans.start() + ", end=" + spans.end());
              add(n1, n2);
              cnt++;
            }
            if (cnt > 0) {
              add(n, n1);
              setString(n1, "text", "Spans (" + cnt + "):");
              setBoolean(n1, "expanded", false);
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          n1 = create("node");
          setString(n1, "text", "Spans Exception: " + e.getMessage());
          add(n, n1);
        }
      }
    } else {
      Object n1 = create("node");
      String defField = getDefaultField(find("srchOptTabs"));
      setString(n1, "text", q.getClass().getName() + ": " + q.toString(defField));
      add(n, n1);
    }
  }
  /**
   * Update the parsed and rewritten query views.
   *
   */
  public void showParsed() {
    Object qField = find("qField");
    Object qFieldParsed = find("qFieldParsed");
    Object qFieldRewritten = find("qFieldRewritten");
    String queryS = getString(qField, "text");
    if (queryS.trim().equals("")) {
      setString(qFieldParsed, "text", "<empty query>");
      setBoolean(qFieldParsed, "enabled", false);
      return;
    } else {
      setBoolean(qFieldParsed, "enabled", true);
    }
    try {
      Query q = createQuery(queryS);
      setString(qFieldParsed, "text", q.toString());
      putProperty(qField, "qParsed", q);
      q = q.rewrite(ir);
      setString(qFieldRewritten, "text", q.toString());
      putProperty(qField, "qRewritten", q);
    } catch (Throwable t) {
      setString(qFieldParsed, "text", t.getMessage());
      setString(qFieldRewritten, "text", t.getMessage());
    }
  }

  /**
   * Perform a search. NOTE: this method is usually invoked from the GUI.
   * @param qField Thinlet widget containing the query
   */
  public void search(Object qField) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    String queryS = getString(qField, "text");
    if (queryS.trim().equals("")) {
      showStatus("FAILED: Empty query.");
      return;
    }
    Object srchOpts = find("srchOptTabs");
    // query parser opts
    Similarity sim = createSimilarity(srchOpts);
    AccessibleHitCollector col;
    try {
      col = createCollector(srchOpts);
    } catch (Throwable t) {
      errorMsg("ERROR creating Collector: " + t.getMessage());
      return;
    }
    Object sTable = find("sTable");
    Object cntRepeat = find("cntRepeat");
    int repeat = Integer.parseInt(getString(cntRepeat, "text"));
    removeAll(sTable);
    Query q = null;
    try {
      q = createQuery(queryS);
      is.setSimilarity(sim);
      showParsed();
      _search(q, is, col, sTable, repeat);
    } catch (Throwable e) {
      e.printStackTrace();
      errorMsg(e.getMessage());
    }
  }
  
  int resStart = 0;
  int resCount = 20;
  LimitedException le = null;

  private void _search(final Query q, final IndexSearcher is,
          AccessibleHitCollector hc, final Object sTable, final int repeat) throws Exception {
    if (hc == null) {
      hc = new AccessibleTopHitCollector(1000, true, true);
    }
    final AccessibleHitCollector collector = hc;
    le = null;
    SlowThread t = new SlowThread(this) {
      public void execute() {
        long startTime = System.nanoTime();
        for (int i = 0; i < repeat; i++) {
          if (i > 0) {
            collector.reset();
          }
          try {
            is.search(q, collector);
          } catch (LimitedException e) {
            le = e;
          } catch (Throwable th) {
            th.printStackTrace();
            errorMsg("ERROR searching: " + th.toString());
            return;
          }
        }
        long endTime = System.nanoTime();
        long delta = (endTime - startTime) / 1000 / repeat;
        String msg;
        if (delta > 100000) {
          msg = delta / 1000 + " ms";
        } else {
          msg = delta + " us";
        }
        if (repeat > 1) {
          msg += " (avg of " + repeat + " runs)";
        }
        showSearchStatus(msg);
        Object bsPrev = find("bsPrev");
        Object bsNext = find("bsNext");
        setBoolean(bsNext, "enabled", false);
        setBoolean(bsPrev, "enabled", false);
        int resNum = collector.getTotalHits();
        if (resNum == 0) {
          Object row = create("row");
          Object cell = create("cell");
          add(sTable, row);
          add(row, cell);
          cell = create("cell");
          add(row, cell);
          cell = create("cell");
          setString(cell, "text", "No Results");
          setBoolean(cell, "enabled", false);
          add(row, cell);
          setString(find("resNum"), "text", "0");
          return;
        }

        if (resNum > resCount) {
          setBoolean(bsNext, "enabled", true);
        }
        setString(find("resNum"), "text", String.valueOf(resNum));
        putProperty(sTable, "resNum", new Integer(resNum));
        putProperty(sTable, "query", q);
        putProperty(sTable, "hc", collector);
        if (le != null) {
          putProperty(sTable, "le", le);
        }
        resStart = 0;
        _showSearchPage(sTable);
      }
    };
    if (slowAccess) {
      t.start();
    } else {
      t.execute();
    }
  }
  
  public void prevPage(Object sTable) {
    int resNum = ((Integer)getProperty(sTable, "resNum")).intValue();
    if (resStart == 0) {
      setBoolean(find("bsPrev"), "enabled", false);
      return;
    }
    resStart -= resCount;
    if (resStart < 0) resStart = 0;
    if (resStart - resCount < 0)
      setBoolean(find("bsPrev"), "enabled", false);
    if (resStart + resCount < resNum)
      setBoolean(find("bsNext"), "enabled", true);      
    _showSearchPage(sTable);
  }
  
  public void nextPage(Object sTable) {
    int resNum = ((Integer)getProperty(sTable, "resNum")).intValue();
    resStart += resCount;
    if (resStart >= resNum) {
      resStart -= resCount;
      setBoolean(find("bsNext"), "enabled", false);
      return;
    }
    setBoolean(find("bsPrev"), "enabled", true);
    if (resStart + resCount >= resNum) {
      setBoolean(find("bsNext"), "enabled", false);
    }
    _showSearchPage(sTable);
  }
  
  private void _showSearchPage(final Object sTable) {
    SlowThread t = new SlowThread(this) {
      public void execute() {
        try {
          removeAll(sTable);
          AccessibleHitCollector hc = (AccessibleHitCollector)getProperty(sTable, "hc");
          int resNum = hc.getTotalHits();
          int max = Math.min(resNum, resStart + resCount);
          Object posLabel = find("resPos");
          setString(posLabel, "text", resStart + "-" + (max - 1));
          for (int i = resStart; i < max; i++) {
            int docid = hc.getDocId(i);
            float score = hc.getScore(i);
            _createResultRow(i, docid, score, sTable);
          }
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }
      }
    };
    if (slowAccess) {
      t.start();
    } else {
      t.execute();
    }
  }
  
  private void _createResultRow(int pos, int docId, float score, Object sTable) throws IOException {
    Object row = create("row");
    Object cell = create("cell");
    add(sTable, row);
    setString(cell, "text", String.valueOf(pos));
    setChoice(cell, "alignment", "right");
    add(row, cell);
    cell = create("cell");
    setString(cell, "text", String.valueOf(df.format(score)));
    setChoice(cell, "alignment", "right");
    add(row, cell);
    cell = create("cell");
    setString(cell, "text", String.valueOf(docId));
    setChoice(cell, "alignment", "right");
    add(row, cell);
    Document doc = ir.document(docId);
    putProperty(row, "docid", new Integer(docId));
    StringBuffer vals = new StringBuffer();
    for (int j = 0; j < idxFields.length; j++) {
      cell = create("cell");
      Decoder dec = decoders.get(idxFields[j]);
      if (dec == null) dec = defDecoder;
      String[] values = doc.getValues(idxFields[j]);
      vals.setLength(0);
      boolean decodeErr = false;
      if (values != null) for (int k = 0; k < values.length; k++) {
        if (k > 0) vals.append(' ');
        String v;
        try {
          v = dec.decodeStored(idxFields[j], values[k]);
        } catch (Throwable e) {
          v = values[k];
          decodeErr = true;
        }
        vals.append(Util.escape(v));
      }
      setString(cell, "text", vals.toString());
      if (decodeErr) {
        setColor(cell, "foreground", Color.RED);
      }
      add(row, cell);
    }
  }

  /**
   * Pop up a modal dialog explaining the selected result.
   * @param sTable Thinlet table widget containing selected search result.
   */
  public void explainResult(Object sTable) {
    Object row = getSelectedItem(sTable);
    if (row == null) return;
    final Integer docid = (Integer) getProperty(row, "docid");
    if (docid == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    final Query q = (Query) getProperty(sTable, "query");
    if (q == null) return;
    Thread t = new Thread() {
      public void run() {
        try {
          IndexSearcher is = new IndexSearcher(ir);
          is.setSimilarity(createSimilarity(find("srchOptTabs")));
          Explanation expl = is.explain(q, docid.intValue());
          Object dialog = addComponent(null, "/xml/explain.xml", null, null);
          Object eTree = find(dialog, "eTree");
          addNode(eTree, expl);
          //setBoolean(eTree, "expand", true);
          add(dialog);
        } catch (Exception e) {
          e.printStackTrace();
          errorMsg(e.getMessage());
        }        
      }
    };
    if (slowAccess) {
      t.start();
    } else {
      t.run();
    }
  }

  private DecimalFormat df = new DecimalFormat("0.0000");
  private String xmlQueryParserFactoryClassName=CorePlusExtensionsParserFactory.class.getName();

  private void addNode(Object tree, Explanation expl) {
    Object node = create("node");
    setString(node, "text", df.format((double) expl.getValue()) + "  " + expl.getDescription());
    add(tree, node);
    if (getClass(tree) == "tree") {
      setFont(node, getFont().deriveFont(Font.BOLD));
    }
    Explanation[] kids = expl.getDetails();
    if (kids != null && kids.length > 0) {
      for (int i = 0; i < kids.length; i++) {
        addNode(node, kids[i]);
      }
    }
  }

  public void gotoDoc(Object sTable) {
    Object row = getSelectedItem(sTable);
    if (row == null) return;
    final Integer docid = (Integer) getProperty(row, "docid");
    if (docid == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        Document doc = null;
        try {
          doc = ir.document(docid.intValue());
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
          return;
        }
        _showDocFields(docid.intValue(), doc);
        Object tabpane = find("maintpane");
        setInteger(tabpane, "selected", 1);
        repaint();        
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  private void _showTermDoc(Object fText, final TermPositions td) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    SlowThread st = new SlowThread(this) {
      public void execute() {
        try {
          Document doc = ir.document(td.doc());
          setString(find("docNum"), "text", String.valueOf(td.doc()));
          setString(find("tFreq"), "text", String.valueOf(td.freq()));
          _showDocFields(td.doc(), doc);          
        } catch (Exception e) {
          e.printStackTrace();
          showStatus(e.getMessage());
        }
      }
    };
    if (slowAccess) {
      st.start();
    } else {
      st.execute();
    }
  }

  public void deleteTermDoc(Object fText) {
    Term t = (Term) getProperty(fText, "term");
    if (t == null) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    try {
      showNextTerm(find("fCombo"), fText);
      ir.deleteDocuments(t);
    } catch (Exception e) {
      e.printStackTrace();
      showStatus(e.getMessage());
    }
    initOverview();
  }

  public void deleteDoc(Object docNum) {
    int docid = 0;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    try {
      docid = Integer.parseInt(getString(docNum, "text"));
      ir.deleteDocument(docid);
      showDoc(docNum);
      initOverview();
      showFiles(dir, Collections.EMPTY_LIST);
      showStatus("Document #" + docid + " deleted OK.");
    } catch (Exception e) {
      showStatus(e.getMessage());
      e.printStackTrace();
    }

  }
  
  public void actionDeleteDocList(Object docList) {
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    try {
      String list = getString(docList, "text");
      Ranges ranges = Ranges.parse(list);
      if (ranges.cardinality() == 0) {
        infoMsg("Empty list - no documents deleted.");
        return;
      }
      long count = ranges.cardinality();
      DocIdSetIterator it = ranges.iterator();
      int doc;
      while ((doc = it.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
        ir.deleteDocument(doc);
      }
      showDoc(find("docNum"));
      initOverview();
      showFiles(dir, Collections.EMPTY_LIST);
      showStatus(count + " documents marked as deleted.");
    } catch (Exception e) {
      errorMsg("Error: " + e.toString());
      e.printStackTrace();
    }

  }

  public void deleteDocList(Object searchTable) {
    Object[] rows = getSelectedItems(searchTable);
    if (rows == null || rows.length == 0) return;
    if (ir == null) {
      showStatus(MSG_NOINDEX);
      return;
    }
    if (readOnly) {
      showStatus(MSG_READONLY);
      return;
    }
    for (int i = 0; i < rows.length; i++) {
      Integer docId = (Integer) getProperty(rows[i], "docid");
      if (docId == null) continue;
      try {
        ir.deleteDocument(docId.intValue());
      } catch (Exception e) {
        continue;
      }
      remove(rows[i]);
    }
    try {
      initOverview();
      showFiles(dir, Collections.EMPTY_LIST);
    } catch (Exception e) {
      errorMsg("Error: " + e.toString());
    }
  }

  public void actionAbout() {
    Object about = addComponent(this, "/xml/about.xml", null, null);
    Object lver = find(about, "lver");
    setString(lver, "text", "Lucene version: " + LucenePackage.get().getImplementationVersion());
  }

  /**
   * Pop up a modal font selection dialog.
   *
   */
  public void actionShowFonts() {
    addComponent(this, "/xml/selfont.xml", null, null);
  }
  
  /**
   * Initialize the font selection dialog.
   * @param selfont font selection dialog
   */
  public void setupSelFont(Object selfont) {
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    Font[] fonts = ge.getAllFonts();
    Object cbFonts = find(selfont, "fonts");
    String curfont = getFont().getFontName();
    float cursize = getFont().getSize2D();
    Object fsize = find(selfont, "fsize");
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(1);
    setString(fsize, "text", nf.format(cursize));
    removeAll(cbFonts);
    Object def = create("choice");
    setFont(def, "font", getFont().deriveFont(15.0f));
    setString(def, "text", curfont + " (default)");
    putProperty(def, "fnt", getFont());
    add(cbFonts, def);
    setInteger(cbFonts, "selected", 0);
    for (int i = 0; i < fonts.length; i++) {
      Object choice = create("choice");
      setFont(choice, "font", fonts[i].deriveFont(15.0f));
      setString(choice, "text", fonts[i].getFontName());
      putProperty(choice, "fnt", fonts[i]);
      add(cbFonts, choice);
      if (curfont.equalsIgnoreCase(fonts[i].getFontName()))
        setInteger(cbFonts, "selected", i + 1);
    }
  }
  
  /**
   * Show preview of the selected font.
   * @param selfont font selection dialog
   */
  public void selectFont(Object selfont) {
    Object preview = find(selfont, "fpreview");
    Object cbFonts = find(selfont, "fonts");
    Object fsize = find(selfont, "fsize");
    Font f = (Font)getProperty(getSelectedItem(cbFonts), "fnt");
    float size = getFont().getSize2D();
    try {
      size = Float.parseFloat(getString(fsize, "text"));
    } catch (Exception e) {
      e.printStackTrace();
    }
    f = f.deriveFont(size);
    Object[] items = getItems(preview);
    for (int i = 0; i < items.length; i++) {
      setPreviewFont(f, items[i]);
    }
  }
  
  private void setPreviewFont(Font f, Object item) {
    try {
      setFont(item, "font", f);
    } catch (IllegalArgumentException iae) {
      // shrug...
    }
    Object[] items = getItems(item);
    for (int i = 0; i < items.length; i++) {
      setPreviewFont(f, items[i]);
    }
  }
  
  /**
   * Set the default font in the UI.
   * @param selfont font selection dialog
   */
  public void actionSetFont(Object selfont) {
    Object cbFonts = find(selfont, "fonts");
    Object fsize = find(selfont, "fsize");
    Font f = (Font)getProperty(getSelectedItem(cbFonts), "fnt");
    float size = getFont().getSize2D();
    try {
      size = Float.parseFloat(getString(fsize, "text"));
    } catch (Exception e) {
      e.printStackTrace();
    }
    f = f.deriveFont(size);
    remove(selfont);
    setFont(f);
    courier = new Font("Courier", getFont().getStyle(), getFont().getSize());
    repaint();
  }
  
  public void actionSetDecoder(Object fList, Object combo) {
    Object row = getSelectedItem(fList);
    if (row == null) {
      return;
    }
    String fName = (String)getProperty(row, "fName");
    Object choice = getSelectedItem(combo);
    String decName = getString(choice, "name");
    Decoder dec = null;
    if (decName.equals("s")) {
      dec = new StringDecoder();
    } else if (decName.equals("b")) {
      dec = new BinaryDecoder();
    } else if (decName.equals("d")) {
      dec = new DateDecoder();
    } else if (decName.equals("nl")) {
      dec = new NumLongDecoder();
    } else if (decName.equals("nd")) {
      dec = new NumDoubleDecoder();
    } else if (decName.equals("ni")) {
      dec = new NumIntDecoder();
    } else if (decName.equals("nf")) {
      dec = new NumFloatDecoder();
    } else if (decName.equals("od")) {
      dec = new OldDateFieldDecoder();
    } else if (decName.equals("on")) {
      dec = new OldNumberToolsDecoder();
    } else {
      dec = defDecoder;
    }
    decoders.put(fName, dec);
    Object cell = getItem(row, 3);
    setString(cell, "text", dec.toString());
    repaint(fList);
    actionTopTerms(find("nTerms"));
  }
  
  /**
   * Returns current custom similarity implementation.
   * @return
   */
  public Similarity getCustomSimilarity() {
    return similarity;
  }
  
  /**
   * Set the current custom similarity implementation.
   * @param s
   */
  public void setCustomSimilarity(Similarity s) {
    similarity = s;
    Object cbSimCust = find("ckSimCust");
    Object cbSimDef = find("ckSimDef");
    Object simName = find("simName");
    if (similarity != null) {
      setString(simName, "text", similarity.getClass().getName());
      setBoolean(cbSimCust, "enabled", true);
    } else {
      setString(simName, "text", "");
      setBoolean(cbSimCust, "enabled", false);
      setBoolean(cbSimDef, "selected", true);
      setBoolean(cbSimCust, "selected", false);
    }
  }
  
  /**
   * Switch the view to display the SimilarityDesigner plugin, if present.
   *
   */
  public void actionDesignSimilarity() {
    LukePlugin designer = null;
    for (int i = 0; i < plugins.size(); i++) {
      if (plugins.get(i).getClass().getName().equals("org.getopt.luke.plugins.SimilarityDesignerPlugin")) {
        designer = (LukePlugin)plugins.get(i);
        break;
      }
    }
    if (designer == null) {
      showStatus("Designer Plugin not available");
      return;
    }
    // a bit tricky: plugins are put within panels, and these in tabs
    Object pluginsTab = find("pluginsTab");
    Object maintab = getParent(pluginsTab);
    int index = getIndex(maintab, pluginsTab);
    setInteger(maintab, "selected", index);
    Object pluginsTabs = find("pluginsTabs");
    Object tab = getParent(getParent(designer.getMyUi()));
    index = getIndex(pluginsTabs, tab);
    setInteger(pluginsTabs, "selected", index);
    repaint();
  }
  
  /**
   * Shut down Luke. If {@link #exitOnDestroy} is true (such as when Luke was
   * started from the main method), invoke also System.exit().
   */
  public boolean destroy() {
    if (ir != null) try {
      ir.close();
    } catch (Exception e) {}
    ;
    if (dir != null) try {
      dir.close();
    } catch (Exception e) {}
    ;
    try {
      Prefs.save();
    } catch (Exception e) {}
    ;
    if (exitOnDestroy) System.exit(0);
    return super.destroy();
  }

  public void actionExit() {
    destroy();
  }

  /**
   * Open URL in the system default browser.
   * @param url
   */
  public void goUrl(Object url) {
    String u = (String) getProperty(url, "url");
    if (u == null) return;
    try {
      BrowserLauncher.openURL(u);
    } catch (Exception e) {
      e.printStackTrace();
      showStatus(e.getMessage());
    }
  }

  /**
   * Start the GUI, and optionally open an index.
   * @param args index parameters
   * @return fully initialized Luke instance
   */
  public static Luke startLuke(String[] args) {
    Luke luke = new Luke();
    FrameLauncher f = new FrameLauncher("Luke - Lucene Index Toolbox, v 3.0.3 (2010-12-31)", luke, 800, 600);
    f.setIconImage(Toolkit.getDefaultToolkit().createImage(Luke.class.getResource("/img/luke.gif")));
    if (args.length > 0) {
      boolean force = false, ro = false, ramdir = false;
      String pName = null;
      String script = null;
      String xmlQueryParserFactoryClassName = null;
      for (int i = 0; i < args.length; i++) {
        if (args[i].equalsIgnoreCase("-ro")) ro = true;
        else if (args[i].equalsIgnoreCase("-force")) force = true;
        else if (args[i].equalsIgnoreCase("-ramdir")) ramdir = true;
        else if (args[i].equalsIgnoreCase("-index")) pName = args[++i];
        else if (args[i].equalsIgnoreCase("-script")) script = args[++i];
        else if (args[i].equalsIgnoreCase("-xmlQueryParserFactory")) xmlQueryParserFactoryClassName = args[++i];
        else {
          System.err.println("Unknown argument: " + args[i]);
          usage();
          luke.actionExit();
          return null;
        }
      }
      if (pName != null) luke.openIndex(pName, force, null, ro, ramdir, false, null, 1);
      if(xmlQueryParserFactoryClassName != null) luke.setParserFactoryClassName(xmlQueryParserFactoryClassName);
      if (script != null) {
        LukePlugin plugin = luke.getPlugin("org.getopt.luke.plugins.ScriptingPlugin");
        if (plugin == null) {
          String msg = "ScriptingPlugin not present - cannot execute scripts.";
          System.err.println(msg);
          luke.actionExit();
        } else {
          ((ScriptingPlugin)plugin).execute("load('" + script + "');");
        }
      }
    } else luke.actionOpen();
    return luke;
  }
  
  private void setParserFactoryClassName(String xmlQueryParserFactoryClassName)  {
	  this.xmlQueryParserFactoryClassName = xmlQueryParserFactoryClassName;	
  }

/**
   * Main method. If you just want to instantiate Luke from other classes or scripts,
   * use {@link #startLuke(String[])} instead.
   * @param args
   */
  public static void main(String[] args) {
    exitOnDestroy = true;
    startLuke(args);
  }

  public static void usage() {
    System.err.println("Command-line usage:\n");
    System.err.println("Luke [-index path_to_index] [-ro] [-force] [-mmap] [-script filename]\n");
    System.err.println("\t-index path_to_index\topen this index");
    System.err.println("\t-ro\topen index read-only");
    System.err.println("\t-force\tforce unlock if the index is locked (use with caution)");
    System.err.println("\t-xmlQueryParserFactory\tFactory for loading custom XMLQueryParsers. E.g.:");
    System.err.println("\t\t\torg.getopt.luke.xmlQuery.CoreParserFactory (default)");
    System.err.println("\t\t\torg.getopt.luke.xmlQuery.CorePlusExtensionsParserFactory");
    System.err.println("\t-mmap\tuse MMapDirectory");
    System.err.println("\t-script filename\trun this script using the ScriptingPlugin.");
    System.err.println("\t\tIf an index name is specified, the index is open prior to");
    System.err.println("\t\tstarting the script. Note that you need to escape special");
    System.err.println("\t\tcharacters twice - first for shell and then for JavaScript.");
    
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see java.awt.datatransfer.ClipboardOwner#lostOwnership(java.awt.datatransfer.Clipboard,
   *      java.awt.datatransfer.Transferable)
   */
  public void lostOwnership(Clipboard arg0, Transferable arg1) {

  }

  /**
   * @return the numTerms
   */
  public int getNumTerms() {
    return numTerms;
  }

}

