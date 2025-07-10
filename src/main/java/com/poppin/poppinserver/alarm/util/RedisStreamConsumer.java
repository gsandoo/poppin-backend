package com.poppin.poppinserver.alarm.util;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.poppin.poppinserver.alarm.usecase.AlarmListQueryUseCase;
import com.poppin.poppinserver.alarm.usecase.TokenQueryUseCase;
import com.poppin.poppinserver.core.config.APNsConfiguration;
import com.poppin.poppinserver.core.config.AndroidConfiguration;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.XClaimParams;
import redis.clients.jedis.params.XPendingParams;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;
import redis.clients.jedis.resps.StreamPendingEntry;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisStreamConsumer {

    private final FirebaseMessaging firebaseMessaging;
    private final APNsConfiguration apnsConfiguration;
    private final AndroidConfiguration androidConfiguration;
    private final AlarmListQueryUseCase alarmListQueryUseCase;
    private final TokenQueryUseCase tokenQueryUseCase;

    private final String STREAM_KEY = "stream:inform_alarm";
    private final String DLQ_KEY = "stream:dlq:inform_alarm";
    private final String GROUP_NAME = "inform_group";
    private final String CONSUMER_NAME = "inform_worker";
    private final UnifiedJedis jedis = new JedisPooled("localhost", 6379);
    private final int MAX_RETRY = 3;

    @PostConstruct
    public void start() {
        new Thread(this::consume).start();
    }

    public void consume() {
        try {
            jedis.xgroupCreate(STREAM_KEY, GROUP_NAME, new StreamEntryID(0, 0), true);
        } catch (Exception e) {
            log.info("이미 존재하는 Consumer Group: {}", e.getMessage());
        }

        while (true) {
            try {
                // 1. 새 메시지 처리
                List<Map.Entry<String, List<StreamEntry>>> messages = jedis.xreadGroup(
                        GROUP_NAME,
                        CONSUMER_NAME,
                        XReadGroupParams.xReadGroupParams().block(2000).count(10),
                        Map.of(STREAM_KEY, StreamEntryID.UNRECEIVED_ENTRY)
                );
                if (messages != null) {
                    processEntries(messages);
                }

                // 2. Pending 메시지 재처리
                List<StreamPendingEntry> pendings = jedis.xpending(
                        STREAM_KEY,
                        GROUP_NAME,
                        XPendingParams.xPendingParams().count(10)
                );

                for (StreamPendingEntry pending : pendings) {
                    if (pending.getIdleTime() > 60000) {
                        StreamEntryID[] ids = {pending.getID()};

                        List<StreamEntry> claimed = jedis.xclaim(
                                STREAM_KEY,
                                GROUP_NAME,
                                CONSUMER_NAME,
                                60000L,
                                XClaimParams.xClaimParams(),
                                ids
                        );

                        if (claimed != null && !claimed.isEmpty()) {
                            List<Map.Entry<String, List<StreamEntry>>> wrapped =
                                    List.of(new AbstractMap.SimpleEntry<>(STREAM_KEY, claimed));
                            processEntries(wrapped);
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Redis Stream 소비 중 에러: {}", e.getMessage(), e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private void processEntries(List<Map.Entry<String, List<StreamEntry>>> messages) {
        for (Map.Entry<String, List<StreamEntry>> stream : messages) {
            for (StreamEntry entry : stream.getValue()) {
                Map<String, String> data = new HashMap<>(entry.getFields());

                try {
                    String token = data.get("token");
                    String title = data.get("title");
                    String body = data.get("body");
                    String userId = data.get("userId");
                    String alarmId = data.get("alarmId");

                    int badge = alarmListQueryUseCase.countUnreadAlarms(Long.parseLong(userId));

                    Message message = Message.builder()
                            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                            .setApnsConfig(apnsConfiguration.apnsConfig(badge))
                            .setAndroidConfig(androidConfiguration.androidConfig())
                            .setToken(token)
                            .putData("id", alarmId)
                            .putData("type", "inform")
                            .build();

                    String result = firebaseMessaging.send(message);
                    log.info("FCM 전송 성공: {}", result);

                    jedis.xack(STREAM_KEY, GROUP_NAME, entry.getID());

                } catch (Exception e) {
                    log.warn("FCM 전송 실패: {}", e.getMessage());

                    int retry = Integer.parseInt(data.getOrDefault("retry", "0"));

                    if (retry >= MAX_RETRY) {
                        log.warn("DLQ로 이동: {}", data);
                        jedis.xadd(DLQ_KEY, StreamEntryID.NEW_ENTRY, data);
                    } else {
                        data.put("retry", String.valueOf(retry + 1));
                        jedis.xadd(STREAM_KEY, StreamEntryID.NEW_ENTRY, data);
                    }

                    jedis.xack(STREAM_KEY, GROUP_NAME, entry.getID());
                }
            }
        }

        // trimming
        jedis.xtrim(STREAM_KEY, 1000, true);
    }

}
