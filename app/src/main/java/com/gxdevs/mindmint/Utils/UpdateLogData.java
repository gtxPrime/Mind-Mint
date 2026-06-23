package com.gxdevs.mindmint.Utils;

import com.gxdevs.mindmint.Models.UpdateLogItem;

import java.util.ArrayList;
import java.util.List;

public class UpdateLogData {
        public static List<UpdateLogItem> getLogs() {
                List<UpdateLogItem> list = new ArrayList<>();

                addVersion(list, "Pumpkin 14 (Current)",
                                "Added new platforms to App Blocker list: Facebook, LinkedIn, Reddit, TikTok, and Twitter",
                                "Added automatic support for modded/lite packages (e.g. Facebook Lite, Instagram Lite, Twitter Lite) grouped under main apps",
                                "Fixed bugs of new Blocker Control",
                                "Upgraded Focus & Locked-In mode notifications to support Android 16 Live Updates.",
                                "Ticking progress bar in ongoing notifications now moves in real-time",
                                "Fixed and updated notification icons.",
                                "Bug fixes and performance improvements");

                addVersion(list, "Pumpkin 13",
                                "Redesigned the Blocker Control and Lock Challenge screens for a cleaner look",
                                "Unified the App Blocker settings for better reliability",
                                "Added different challenge types: Scream, Shake, Maths and Breathing",
                                "Added screenshot and screen recording protection to all challenge screens",
                                "Prevented false scroll counts when opening or closing Instagram comments",
                                "Live scroll counter pill now shows strictly inside Reels/Shorts/Spotlights feeds",
                                "Smarter section-scoped blockers: challenge triggers once per visit to Reels/Shorts",
                                "Various bugs and performance fixes");

                addVersion(list, "Pumpkin 12",
                                "Improved prevent uninstall working",
                                "Added subtle animations",
                                "Added option to add apps to whitelist while Lock In mode",
                                "Fixed bugs in Locked In mode",
                                "Made the blocking more robust",
                                "Fixed minor bugs");

                addVersion(list, "Pumpkin 11",
                                "Prevent uninstall - stop anyone from removing the app without your permission",
                                "Lock types coming soon - more ways to protect your settings",
                                "Added Task-Linked Focus Mode",
                                "Added Scheduled Focus sessions",
                                "Enhanced Lock In mode",
                                "Added swipe gestures for smoother navigation",
                                "Bug fixes and improvements");

                addVersion(list, "Pumpkin 10",
                                "Implemented lock protection on settings",
                                "Implemented live scroll counter",
                                "Improved blocking mechanism",
                                "Major / Minor bugs fixed");

                addVersion(list, "Pumpkin 9",
                                "Added dedicated stats for all habits (tap on any habit)",
                                "Added in depth overall stats for - Habits, Focus and Tasks",
                                "Redesigned stats screen",
                                "Introduced new streak system to habits",
                                "Added goal system to habits",
                                "Added mood / emotion logging to habits",
                                "Added dedicated settings for Focus Mode",
                                "Added Pomodoro Timer in focus mode",
                                "Added auto break start switch",
                                "Added topics selection in focus mode",
                                "Added 5 widgets for home screen (more will be added in future)",
                                "Improved blocking mechanism");

                addVersion(list, "Pumpkin 8",
                                "Huge improvement in UI",
                                "Improved UX",
                                "Added keep service alive switch in settings",
                                "Improvement in stats",
                                "Bug fixes");

                addVersion(list, "Pumpkin 7",
                                "Added Adult content blocker",
                                "Fixed bugs",
                                "Made all permissions optional");

                addVersion(list, "Pumpkin 6",
                                "Made accessibility permission optional",
                                "Made battery optimization permission optional",
                                "Added blocking on browsers",
                                "Introduced new in app currency",
                                "Introduced custom theme selection");

                addVersion(list, "Pumpkin 5",
                                "Huge improvement in UI",
                                "Added pause blocker feature",
                                "Added task manager",
                                "Added habit manager",
                                "Brief overview of stats");

                addVersion(list, "Pumpkin 4",
                                "PlayStore launch");

                addVersion(list, "Pumpkin 3",
                                "Initial beta v3");

                addVersion(list, "Pumpkin 2",
                                "Initial beta v2");

                addVersion(list, "Pumpkin 1",
                                "Initial beta launch");

                return list;
        }

        private static void addVersion(List<UpdateLogItem> list, String version, String... changes) {
                list.add(new UpdateLogItem(UpdateLogItem.TYPE_HEADER, version));
                for (String change : changes) {
                        list.add(new UpdateLogItem(UpdateLogItem.TYPE_ITEM, change));
                }
        }
}
