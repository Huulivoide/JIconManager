/**
 * Copyright 2015 Jesse Jaara <jesse.jaara@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fi.Huulivoide.JIconManager;

import org.omg.CORBA.portable.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ImageIcon;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.HashMap;

/**
 * JIconManager allows you to easily access all of the icons you have bundled
 * with your application. If the icon naming scheme matches Freedesktop's
 * <a href="http://standards.freedesktop.org/icon-naming-spec/icon-naming-spec-latest.html">
 * Icon Naming specification</a> JIconManager can load the icons from a systemwide icon theme.
 *
 * In Linux nearly every single application uses the same icons, which quarantines consistent
 * look in every application. Java's Swing sadly has no build-in support for these icon themes.
 * JIconManager fixes that problem, making your Swing applications first class citizens in
 * any Linux system.
 *
 * @author <a href="mailto:jesse.jaara@gmail.com">Jesse Jaara</a>
 * @version %I%
 */
public class JIconManager
{
    //    region Public    //
    /////////////////////////

    /**
     * Use system's/user's default theme
     */
    public static final String DEFAULT_THEME = "default";

    /**
     * Construct an JIconManager that only uses bundled icons.
     *
     * If the theme file is not located directly in the default filesystem, the path must be
     * constructed by using appropriate
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystems.html">
     * java.nio.file.FileSystem
     * </a>
     * object. The used FileSystem is expected to stay open at least until the application has
     * requested all of the icons it needs.
     *
     * <br><br>
     *
     * Theme file must comfort to
     * <a href="http://standards.freedesktop.org/icon-theme-spec/icon-theme-spec-latest.html">
     * Freedesktop.org's Icon Theme Specification
     * </a>
     *
     * @param applicationThemeFilePath path to the index.theme file
     * @throws MalformedIconThemeFileException in case index.theme has serious flaws
     * @throws ThemeNotFoundException this should never happen, if it does please file a bug report
     */
    public JIconManager(Path applicationThemeFilePath) throws MalformedIconThemeFileException, ThemeNotFoundException
    {
            this(applicationThemeFilePath, null);
    }


    /**
     * Construct an JIconManager that prefers system icons, falling back to bundled icons if needed.
     *
     * If the theme file is not located directly in the default filesystem, the path must be
     * constructed by using appropriate
     * <a href="https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystems.html">FileSystem</a>
     * object. The used FileSystem is expected to stay open at least until the application has
     * requested all of the icons it needs.
     *
     * <br><br>
     *
     * If no fallback icon theme is bundled with the application, one may pass <b>null</b> as the
     * applicationThemFile parameter
     *
     * <br><br>
     *
     * Usually it best to pass <b>DEFAULT_THEME</b> as the systemThemeName, this will ensure that
     * the users default theme is used. Alternatively one can pass a name directly if one so wishes.
     * If the system doesn't support themes i.e. is not any Linux, FreeBSD or OpenBSD.
     * If the given theme is not found an exception is raised.
     *
     * <br><br>
     *
     * Theme files must comfort to
     * <a href="http://standards.freedesktop.org/icon-theme-spec/icon-theme-spec-latest.html">
     * Freedesktop.org's Icon Theme Specification
     * </a>
     *
     *
     * @param applicationThemeFile path to the index.theme file
     * @param systemThemeName name of an icon theme installed on the system
     * @throws MalformedIconThemeFileException in case index.theme has serious flaws
     * @throws ThemeNotFoundException in the specified theme cannot be found
     *
     * @see #DEFAULT_THEME
     */
    public JIconManager(Path applicationThemeFile, String systemThemeName)
            throws MalformedIconThemeFileException, ThemeNotFoundException
    {
        this.applicationTheme = null;
        this.systemTheme = null;

        if (systemThemeName != null && SYSTEM_SUPPORTS_THEMES)
            loadSystemTheme(systemThemeName);


        applicationTheme = new Theme(applicationThemeFile, false);

    }


    /**
     * Load a system icon theme
     *
     * If no system theme was specified in the constructor, óne can be loaded with this method.
     * You can also replace existing theme with this method.
     *
     * @param themeName name of the system theme
     * @return true if new theme was loaded, else false
     * @throws MalformedIconThemeFileException in case index.theme has serious flaws
     * @throws ThemeNotFoundException in the specified theme cannot be found
     *
     * @see #DEFAULT_THEME
     */
    public boolean loadSystemTheme(String themeName) throws MalformedIconThemeFileException, ThemeNotFoundException
    {
        Theme oldTheme = systemTheme;

        if (themeName == DEFAULT_THEME)
            themeName = getDefaultTheme();

        Path themePath = systemThemes.get(themeName);

        if (themePath == null)
            throw new ThemeNotFoundException("Theme '" + themeName + "' is not installed.");


        try
        {
            systemTheme = new Theme(themePath, true);
        }
        catch (MalformedIconThemeFileException e)
        {
            log.error("Cannot load system theme " + themeName + " reason: " + e.getMessage());
        }

        if (systemTheme != oldTheme)
            return true;

        return false;
    }


    /**
     * Fetch the given icon.
     *
     * Fetch the named icon of specified size.
     * If no icon of that name is found from the loaded system theme or the fallback
     * application theme a null value is returned.
     * If no icon of the requested size is found, the largest instance of the names
     * icon will be scaled instead.
     *
     * <br><br>
     *
     * The name of the icon should comfort to
     * <a href="http://standards.freedesktop.org/icon-naming-spec/icon-naming-spec-latest.html">
     * Freedesktop.org's Icon Naming Specification
     * </a>.
     *
     * <br>
     *
     * All requests are cached for later use.
     *
     * @param name name of the requested icon
     * @param size size of the requested icon
     * @return ImageIcon or null if no icon of given name is found
     */
    public ImageIcon getIcon(String name, int size)
    {
        ImageIcon icon = null;

        if (systemTheme != null)
            icon = getSystemIcon(name, size);
        if (icon == null)
            icon = getFromResources(name, size);

        return icon;
    }


    /////////////////////////
    //  endregion Public   //


    //  region Protected   //
    /////////////////////////

    /**
     * Return the path of the index.theme file for the named theme.
     *
     * @param themeName name of the theme
     * @return Path to theme/index.theme
     */
    protected static Path getSystemTheme(String themeName)
    {
        return systemThemes.get(themeName);
    }


    /////////////////////////
    // endregion Protected //


    //   region Private    //
    /////////////////////////

    private String systemThemeName;

    private Theme systemTheme;
    private Theme applicationTheme;


    private static final Logger log = LoggerFactory.getLogger(JIconManager.class);

    private static final boolean SYSTEM_SUPPORTS_THEMES = systemSupportsThemes();

    private static final Path SYSTEM_THEMES_DIRECTORY = Paths.get("/usr/share/icons");
    private static final Path SYSTEM_THEMES_LOCAL_DIRECTORY = Paths.get("/usr/local/share/icons");
    private static final Path USER_HOME_ICONS = getPathFromEnviroinment("HOME", ".icons");
    private static final Path XGD_DATA_DIR_ICONS = getPathFromEnviroinment("XGD_DATA_DIRS", "icons");

    private static final HashMap<String, Path> systemThemes = listInstalledThemes();


    /**
     * Construct to a path to icon directory obtained from an enviroinment variable like HOME or XDG_DATA_DIR
     *
     * @param variable name if the enviroinment variable
     * @param path append to end of the variable
     * @return Path object or null if variable is empty
     */
    private static Path getPathFromEnviroinment(String variable, String path)
    {
        String VARIABLE = System.getenv(variable);
        Path iconPath = null;

        if (VARIABLE != null)
        {
            iconPath = Paths.get(VARIABLE, path);

            if (Files.exists(iconPath))
                return iconPath;
        }

        return null;
    }

    /**
     * Check whether the system supports themes i.e is Linux or some BSD system
     *
     * @return true if supported, false if not
     */
    private static boolean systemSupportsThemes()
    {
        String osName = System.getProperty("os.name");

        // TODO Check if OpenBSD really is "OpenBSD" or something else
        return (osName.equals("Linux") || osName.equals("FreeBSD") || osName.equals("OpenBSD"));
    }

    /**
     * Construct a list all themes installed systemwide or to user's home directory.
     *
     * @return list of installed themes
     */
    private static HashMap<String, Path> listInstalledThemes()
    {
        HashMap<String, Path> foundThemes = new HashMap<>();

        if (systemSupportsThemes())
        {

            if (XGD_DATA_DIR_ICONS != null)
                findThemesFromDirectory(XGD_DATA_DIR_ICONS, foundThemes);

            if (USER_HOME_ICONS != null)
                findThemesFromDirectory(USER_HOME_ICONS, foundThemes);

            if (SYSTEM_THEMES_LOCAL_DIRECTORY != null)
                findThemesFromDirectory(SYSTEM_THEMES_LOCAL_DIRECTORY, foundThemes);

            if (SYSTEM_THEMES_DIRECTORY != null)
                findThemesFromDirectory(SYSTEM_THEMES_DIRECTORY, foundThemes);

        }

        return foundThemes;
    }


    /**
     * Scan given directory for icon themes.
     *
     * Proceed to each direct subdirectory in the given path and look for a file
     * name 'index.theme'.
     *
     * @param iconsDirectoryRoot path to themes-folder root
     * @param foundThemes list of found icon themes
     */
    private static void findThemesFromDirectory(Path iconsDirectoryRoot, HashMap<String, Path> foundThemes)
    {
       try (DirectoryStream<Path> rootStream = Files.newDirectoryStream(iconsDirectoryRoot))
       {
           for (Path candidate : rootStream)
           {
               // Ignore any files we might find in the root, they are of no interest to us
               if (Files.isDirectory(candidate))
               {
                   try (DirectoryStream<Path> subdirStream = Files.newDirectoryStream(candidate))
                   {
                       for (Path file : subdirStream)
                       {
                           if (file.getFileName().toString().equals("index.theme"))
                               foundThemes.put(candidate.getFileName().toString(), file);
                       }
                   }
                   catch (IOException e) // Catch here so we can try the other dirs also
                   {
                       log.warn("Could not process directory '{}' file looking for installed themes." +
                                "Java error message: {}", candidate.toString(), e.getMessage());
                   }
               }
           }
       }
       catch (NotDirectoryException e)
       {
           log.warn("Path '{}' is not an directory and cannot be opened.", iconsDirectoryRoot.toString());
       }
       catch (IOException e)
       {
           log.warn("An error happened while trying to list themes installed in '{}. " +
                    "Error: {}", iconsDirectoryRoot.toString(), e.getMessage());
       }
    }


    private String getDconfTheme()
    {
        String result = null;

        try
        {
            Process dconfProcess = Runtime.getRuntime().exec("dconf /org/gnome/desktop/interface/icon-theme");
            BufferedReader resultStream = new BufferedReader(new InputStreamReader(dconfProcess.getInputStream()));

            result = resultStream.readLine();
        }
        catch (IOException e)
        {
            log.info("Could not determinate default theme from dconf. Reason: " + e.getMessage());
        }

        if (result != null && result.length() > 0)
        {
            result = result.substring(1, result.length() - 1);
        }

        return result;
    }

    /**
     * Try to determinate user's default theme.
     *
     * Tries to solve the theme name from:
     * <ol>
     *     <li>DConf</li>
     * </ol>
     *
     * @return
     */
    private String getDefaultTheme()
    {
        String theme = getDconfTheme();

        if (theme == null)
            theme = DEFAULT_THEME;

        return theme;
    }

    private ImageIcon getFromResources(String name, int size)
    {
        return applicationTheme.getIcon(name, size);
    }

    private ImageIcon getSystemIcon(String name, int size)
    {
        return systemTheme.getIcon(name, size);
    }


    /////////////////////////
    //  endregion Private  //
}
