/*
 Copyright 2009-2015 Urban Airship Inc. All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
 IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.cordova;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.support.v4.app.NotificationCompat;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.notifications.DefaultNotificationFactory;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Urban Airship autopilot to automatically handle takeOff.
 */
public class CordovaAutopilot extends Autopilot {
    static final String UA_PREFIX = "com.urbanairship";
    static final String PRODUCTION_KEY = "com.urbanairship.production_app_key";
    static final String PRODUCTION_SECRET = "com.urbanairship.production_app_secret";
    static final String DEVELOPMENT_KEY = "com.urbanairship.development_app_key";
    static final String DEVELOPMENT_SECRET = "com.urbanairship.development_app_secret";
    static final String IN_PRODUCTION = "com.urbanairship.in_production";
    static final String GCM_SENDER = "com.urbanairship.gcm_sender";
    static final String ENABLE_PUSH_ONLAUNCH = "com.urbanairship.enable_push_onlaunch";
    static final String NOTIFICATION_ICON = "com.urbanairship.notification_icon";
    static final String NOTIFICATION_ACCENT_COLOR = "com.urbanairship.notification_accent_color";

    private PluginConfig pluginConfig;

    @Override
    public AirshipConfigOptions createAirshipConfigOptions(Context context) {
        AirshipConfigOptions options = new AirshipConfigOptions();
        PluginConfig pluginConfig = getPluginConfig(context);

        // Apply any overrides from the manifest
        options.productionAppKey = pluginConfig.getString(PRODUCTION_KEY, options.productionAppKey);
        options.productionAppSecret = pluginConfig.getString(PRODUCTION_SECRET, options.productionAppSecret);
        options.developmentAppKey = pluginConfig.getString(DEVELOPMENT_KEY, options.developmentAppKey);
        options.developmentAppSecret = pluginConfig.getString(DEVELOPMENT_SECRET, options.developmentAppSecret);
        options.gcmSender = pluginConfig.getString(GCM_SENDER, options.gcmSender);
        options.inProduction = pluginConfig.getBoolean(IN_PRODUCTION, options.inProduction);

        // Set the minSDK to 14.  It just controls logging error messages for different platform features.
        options.minSdkVersion = 14;

        return options;
    }

    @Override
    public void onAirshipReady(UAirship airship) {
        Context context = UAirship.getApplicationContext();
        PluginConfig pluginConfig = getPluginConfig(context);

        final boolean enablePushOnLaunch = pluginConfig.getBoolean(ENABLE_PUSH_ONLAUNCH, false);
        if (enablePushOnLaunch) {
            airship.getPushManager().setUserNotificationsEnabled(enablePushOnLaunch);
        }

        // Customize the notification factory
        DefaultNotificationFactory factory = new DefaultNotificationFactory(context);

        // Accent color
        String accentColor = pluginConfig.getString(NOTIFICATION_ACCENT_COLOR, null);
        if (!UAStringUtil.isEmpty(accentColor)) {
            try {
                factory.setColor(Color.parseColor(accentColor));
            } catch (IllegalArgumentException e) {
                Logger.error("Unable to parse notification accent color: " + accentColor, e);
            }
        }

        // Notification icon
        String notificationIconName = pluginConfig.getString(NOTIFICATION_ICON, null);
        if (!UAStringUtil.isEmpty(notificationIconName)) {
            int id  = context.getResources().getIdentifier(notificationIconName, "drawable", context.getPackageName());
            if (id > 0) {
                factory.setSmallIconId(id);
            } else {
                Logger.error("Unable to find notification icon with name: " + notificationIconName);
            }
        }

        airship.getPushManager().setNotificationFactory(factory);
    }

    /**
     * Gets the config for the Urban Airship plugin.
     *
     * @param context The application context.
     * @return The plugin config.
     */
    public PluginConfig getPluginConfig(Context context) {
        if (pluginConfig == null) {
            pluginConfig = new PluginConfig(context);
        }

        return pluginConfig;
    }

    /**
     * Helper class to parse the Urban Airship plugin config from the Cordova config.xml file.
     */
    class PluginConfig {
        private Map<String, String> configValues = new HashMap<String, String>();

        /**
         * Constructor for the PluginConfig.
         * @param context The application context.
         */
        PluginConfig(Context context) {
            parseConfig(context);
        }

        /**
         * Gets a String value from the config.
         *
         * @param key The config key.
         * @param defaultValue Default value if the key does not exist.
         * @return The value of the config, or default value.
         */
        String getString(String key, String defaultValue) {
            return configValues.containsKey(key) ? configValues.get(key) : defaultValue;
        }

        /**
         * Gets a Boolean value from the config.
         *
         * @param key The config key.
         * @param defaultValue Default value if the key does not exist.
         * @return The value of the config, or default value.
         */
        boolean getBoolean(String key, boolean defaultValue) {
            return configValues.containsKey(key) ?
                   Boolean.parseBoolean(configValues.get(key)) : defaultValue;
        }

        /**
         * Parses the config.xml file.
         * @param context The application context.
         */
        private void parseConfig(Context context) {
            int id = context.getResources().getIdentifier("config", "xml", context.getPackageName());
            if (id == 0) {
                return;
            }

            XmlResourceParser xml = context.getResources().getXml(id);

            int eventType = -1;
            while (eventType != XmlResourceParser.END_DOCUMENT) {

                if (eventType == XmlResourceParser.START_TAG) {
                    if (xml.getName().equals("preference")) {
                        String name = xml.getAttributeValue(null, "name").toLowerCase(Locale.US);
                        String value = xml.getAttributeValue(null, "value");

                        if (name.startsWith(UA_PREFIX) && value != null) {
                            configValues.put(name, value);
                            Logger.verbose("Found " + name + " in config.xml with value: " + value);
                        }
                    }
                }

                try {
                    eventType = xml.next();
                } catch (Exception e) {
                    Logger.error("Error parsing config file", e);
                }
            }
        }

    }
}
