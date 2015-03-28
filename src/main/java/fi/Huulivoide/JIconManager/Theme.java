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

import org.imgscalr.Scalr;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

class Theme
{
    //    region Public    //
    /////////////////////////

    public Theme(Path themeFile, boolean isSystemTheme) throws MalformedIconThemeFileException
    {
        inheritedThemes = new ArrayList<>();
        icons = new HashMap<>();

        Path rootPath = themeFile.getParent();
        name = rootPath.getFileName().toString();

        try
        {
            Ini themeData = new Ini(Files.newBufferedReader(themeFile, Charset.forName("UTF-8")));

            // Try to open the theme's info section
            Ini.Section infoSection = themeData.get("Icon Theme");
            if (infoSection == null)
            {
                // Try to see if it is available in all lowercase
                infoSection = themeData.get("icon theme");

                if (infoSection == null) // No :( exit
                {
                    throw new MalformedIconThemeFileException("Section [Icon Theme] missing from file: " +
                                                                      themeFile.toString());
                }
                else
                {
                    log.warn("Malformed theme file '{}': section '[icon theme]' should be '[Icon Theme]'.",
                             themeFile.toString());
                }
            }


            // All themes with the exception of Hicolor can inherit other themes.
            // Hicolor is inherited always, even if the theme doesn't specify it
            // Hicolor itself is naturally an exception.
            // Application theme inheritance is not supported
            if (name.equals(HICOLOR_ICON_THEME_NAME) && isSystemTheme)
               inheritThemes(infoSection, themeFile);


            String foldersKey = themeData.get("Icon Theme", "Directories");
            if (foldersKey == null || foldersKey.length() == 0)
            {
                log.error("Malformed theme file '{}': " +
                          "Section [Icon Theme] does not contain Directories-key or it is empty.");

                throw new MalformedIconThemeFileException("Section [Icon Theme] does not contain proper Directories-key: " +
                                                          themeFile.toString());
            }

            // Scan each directory for icon files
            for (String folder : foldersKey.split(","))
            {
                Integer size = 0;

                String type = themeData.get(folder, "Type");

                if (type.equals("fixed") || type.equals("Fixed"))
                    size = Integer.parseInt(themeData.get(folder, "Size"));
                else if (type.equals("scalable") || type.equals("Scalable"))
                    size = ThemeIcon.SCALABLE;

                // Iterate over to files in the directory
                loadFromPath(rootPath.resolve(folder), size);
            }
        }
        catch (IOException e)
        {
            log.warn(e.toString());
        }

        loadedThemes.add(this);
    }

    public void loadFromPath(Path scanPath, Integer size)
    {
        // Iterate over to files in the directory
        try (DirectoryStream<Path> files = Files.newDirectoryStream(scanPath, "*.png"))
        {
            for (Path iconFile : files)
            {
                String iconName = iconFile.getFileName().toString().split("\\.")[0];

                // Assign symlinks as aliases for the real file
                if (Files.isSymbolicLink(iconFile))
                {
                    Path target = Files.readSymbolicLink(iconFile);
                    ThemeIcon icon = icons.get(target.getFileName().toString().split("\\.")[0]);

                    // We need to load the target icon first
                    if (icon == null)
                        icon = loadIcon(target, size);

                    icons.put(iconName, icon);
                }
                else
                {
                    loadIcon(iconFile, size);
                }
            }
        }
        catch (IOException e)
        {
            log.error("Failed to open DirectoryStream for folder {}", scanPath.toString());
        }
    }

    public ImageIcon getIcon(String iconName, Integer iconSize)
    {
        return getIcon(iconName, iconSize, true);
    }

    /////////////////////////
    //  endregion Public   //


    //  region Protected   //
    /////////////////////////


    /////////////////////////
    // endregion Protected //


    //   region Private    //
    /////////////////////////

    private static final String HICOLOR_ICON_THEME_NAME = "hicolor";
    private static ArrayList<Theme> loadedThemes = new ArrayList<>();
    private static final Logger log = LoggerFactory.getLogger(Theme.class);

    private class ThemeIcon
    {
        public ThemeIcon()
        {
            sizes = new HashMap<>();
            cachedIcons = new HashMap<>();
        }

        public HashMap<Integer, Path> sizes;
        public HashMap<Integer, ImageIcon> cachedIcons;

        public static final int SCALABLE = -1;
    }

    private HashMap<String, ThemeIcon> icons;

    private String name;

    private FileSystem themeRootFs;
    private ArrayList<Theme> inheritedThemes;

    private static boolean themeIsLoaded(String themeName)
    {
        for (Theme theme : loadedThemes)
        {
            if (theme.name.equals(themeName))
                return true;
        }

        return false;
    }

    private static Theme getLoadedTheme(String themeName)
    {
        for (Theme theme : loadedThemes)
        {
            if (theme.name.equals(themeName))
                return theme;
        }

        return null;
    }

    private static String extractThemeName(Profile.Section infoSection, Path themeFile)
    {
        String name = infoSection.get("Name");
        if (name == null)
        {
            name = infoSection.get("name");

            if (name == null)
            {
                log.warn("Malformed theme file '{}': section [Icon Theme] does not contain key 'Name'. " +
                                 "Substituting with path name.", themeFile.toString());

                name = themeFile.getParent().getFileName().toString();
            }
            else
            {
                log.warn("Malformed theme file '{}': [Icon Theme] section's key 'name' should be 'Name'.",
                         themeFile.toString());
            }
        }

        return name;
    }

    private void inheritThemes(Profile.Section infoSection, Path themeFile)
    {
        // See if we can find a list of themes to be inherited
        String inheritsKey = infoSection.get("Inherits");
        if (inheritsKey == null)
        {
            // Try lowercase instead
            inheritsKey = infoSection.get("inherits");

            // Warn about malformed theme file
            if (inheritsKey != null)
                log.warn("Malformed theme file '{}': Section '[inherits]' should be '[Inherits]'.",
                         themeFile.toString());
        }

        ArrayList<String> toBeInherited = new ArrayList<>();
        // Don't cause nullPointerException
        if (inheritsKey != null)
            toBeInherited = new ArrayList<>(Arrays.asList(inheritsKey.split(",")));

        // Make sure hicolor is the last one in the list
        int hicolorIndex = toBeInherited.indexOf(HICOLOR_ICON_THEME_NAME);
        if (hicolorIndex == -1) // not in the list at all
        {
            toBeInherited.add(HICOLOR_ICON_THEME_NAME);
        }
        else if (hicolorIndex != toBeInherited.size() - 1)
        {
            toBeInherited.remove(hicolorIndex);
            toBeInherited.add(HICOLOR_ICON_THEME_NAME);
        }


        for (String inheritedTheme : toBeInherited)
        {

            log.info("Theme '{}' inherits theme '{}'.",
                     themeFile.getParent().getFileName().toString(), inheritedTheme);

            // If theme is not already loaded, then procede to load it
            if (themeIsLoaded(inheritedTheme))
            {
                inheritedThemes.add(getLoadedTheme(inheritedTheme));
            }
            else
            {
                try
                {
                    Path themePath = JIconManager.getSystemTheme(inheritedTheme);
                    if (themePath != null)
                        inheritedThemes.add(new Theme(themePath, true));
                }
                catch (MalformedIconThemeFileException e)
                {
                    log.error("Theme '{}' failed to inherit malformed theme '{}': {}",
                              name, inheritedTheme, e.getMessage());
                }
            }
        }
    }

    private ThemeIcon loadIcon(Path iconFile, Integer size)
    {
        ThemeIcon icon = null;

        String iconName = iconFile.getFileName().toString().split("\\.")[0];

        if (icons.containsKey(iconName) == false)
        {
            icon = new ThemeIcon();
            icons.put(iconName, icon);
        }
        else
        {
            icon = icons.get(iconName);
        }

        icon.sizes.put(size, iconFile);
        log.debug("Icon {} of size {} was added to an IconList", iconName, size);

        return icon;
    }

    private ImageIcon getLocalIcon(String iconName, Integer iconSize)
    {
        int biggest = ThemeIcon.SCALABLE;
        Path iconFile = null;
        ImageIcon icon = null;

        ThemeIcon tIcon = icons.get(iconName);
        if (tIcon != null)
        {
            // See if the icon has been loaded before, if so return it.
            if (tIcon.cachedIcons.containsKey(iconSize))
            {
                log.info("Loading cached icon '{}' of size {} from theme '{}'.", iconName, iconSize, name);
                return tIcon.cachedIcons.get(iconSize);
            }

            Set<Integer> sizes = icons.get(iconName).sizes.keySet();

            if (sizes.contains(iconSize))
            {
                iconFile = tIcon.sizes.get(iconSize);
            }
            else // try to resize the biggest icon
            {
                for (int size : sizes)
                {
                    if (size > biggest)
                        biggest = size;
                }

                iconFile = tIcon.sizes.get(biggest);
            }
        }

        if (iconFile != null)
        {
            try
            {
                BufferedImage iconImg = ImageIO.read(Files.newInputStream(iconFile, StandardOpenOption.READ));

                if (biggest != ThemeIcon.SCALABLE)
                {
                    log.info("Theme '{}' does not have icon '{}' in size {}, resizing from {}",
                             name, iconName, iconSize, biggest);
                    icon = new ImageIcon(Scalr.resize(iconImg, iconSize));
                }
                else  // TODO Implement SVG-rasterization
                {
                    log.info("Loading requested icon '{}' of size {} from theme '{}'.",
                             iconName, iconSize, name);
                    icon = new ImageIcon(iconImg);
                }
            }
            catch (IOException e)
            {
                log.error("Could not load icon from file '{}'.", iconFile.toString());
            }
        }

        // Cache for later use
        if (icon != null)
            tIcon.cachedIcons.put(iconSize, icon);

        return icon;
    }

    private ImageIcon getIcon(String iconName, Integer iconSize, boolean queryHicolor)
    {
        ImageIcon icon = getLocalIcon(iconName, iconSize);

        for (Theme fallback : inheritedThemes)
        {
            if (icon != null)
                break;

            if (fallback.name != HICOLOR_ICON_THEME_NAME)
                icon = fallback.getIcon(iconName, iconSize, false);
            else if (fallback.name == HICOLOR_ICON_THEME_NAME && queryHicolor == true)
                icon = fallback.getIcon(iconName, iconSize, false);
        }

        return icon;
    }

    /////////////////////////
    //  endregion Private  //
}
