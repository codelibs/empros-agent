/*
 * Copyright 2012-2020 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.empros.agent.watcher.twitter;


import java.util.concurrent.atomic.AtomicBoolean;

import org.codelibs.empros.agent.event.Event;
import org.codelibs.empros.agent.event.EventManager;
import org.codelibs.empros.agent.watcher.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;
import twitter4j.User;

public class SampleStreamer implements Watcher{
    private Logger logger = LoggerFactory.getLogger(SampleStreamer.class);

    private EventManager manager = null;

    private TwitterStream stream = null;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final String ID = "tweetId";

    private final String USER_SCREEN_NAME = "screenName";

    private final String TEXT = "text";

    private final String DATE = "date";

    private final String LOCATION = "location";

    private final String PLACE = "place";

    private final String USER_ID = "userId";

    private final String USER_TWEETNUM = "tweetNum";

    private final String USER_FOLLOWING = "following";

    private final String USER_FOLLOWERS = "followers";

    private final String USER_CREATEAT = "createAt";

    private final String USER_LOCATION = "userLocation";


    @Override
    public void start() {
        if(this.manager == null) {
            logger.warn("EventManager is null.");
            return;
        }

        if(started.getAndSet(true)) {
            return;
        }


        stream = TwitterStreamFactory.getSingleton();
        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                User user = status.getUser();
                String timezone = user.getTimeZone();
                String tweet = status.getText();

                boolean isJa = false;
                if(timezone == null) {
                    for(int i=0;i<tweet.length();i++) {
                        char ch = tweet.charAt(i);
                        Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(ch);
                        if(Character.UnicodeBlock.HIRAGANA.equals(unicodeBlock) ||
                                Character.UnicodeBlock.KATAKANA.equals(unicodeBlock) ||
                                Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS.equals(unicodeBlock) ||
                                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS.equals(unicodeBlock) ||
                                Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION.equals(unicodeBlock)) {
                            isJa = true;
                            break;
                        }
                    }
                } else if(timezone.equals("Tokyo") ||
                        timezone.equals("Osaka") ||
                        timezone.equals("Sapporo")) {
                    isJa = true;
                }

                if(isJa && !tweet.startsWith("@")) {
                    Event event = new Event();
                    event.put(ID, status.getId());
                    event.put(USER_SCREEN_NAME, user.getScreenName());
                    event.put(TEXT, status.getText());
                    event.put(DATE, status.getCreatedAt().getTime());
                    if(status.getGeoLocation() != null) {
                        event.put(LOCATION, status.getGeoLocation());
                    } else {
                        event.put(LOCATION, "none");
                    }
                    if(status.getPlace() != null) {
                        event.put(PLACE, status.getPlace());
                    } else {
                        event.put(PLACE, "none");
                    }
                    event.put(USER_ID, user.getId());
                    event.put(USER_TWEETNUM, user.getStatusesCount());
                    event.put(USER_FOLLOWING, user.getFriendsCount());
                    event.put(USER_FOLLOWERS, user.getFollowersCount());
                    event.put(USER_CREATEAT, user.getCreatedAt().getTime());
                    if(user.getLocation() != null){
                        event.put(USER_LOCATION, user.getLocation());
                    } else {
                        event.put(USER_LOCATION, "none");
                    }

                    manager.addEvent(event);
                    manager.submit();
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                //ignore
            }


            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                //ignore
            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
                //ignore
            }

            @Override
            public void onStallWarning(StallWarning warning) {
                //ignore
            }

            @Override
            public void onException(Exception ex) {
                logger.warn("twitter stream exception.", ex);
            }
        };

        stream.addListener(listener);
        stream.sample();
    }

    @Override
    public void stop() {
        if(!started.getAndSet(false) || stream == null) {
            return;
        }
        stream.shutdown();
    }

    @Override
    public void setEventManager(EventManager eventManager) {
        this.manager = eventManager;
    }
}
