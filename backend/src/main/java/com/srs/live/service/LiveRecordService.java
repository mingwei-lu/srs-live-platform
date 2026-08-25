package com.srs.live.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.srs.live.common.util.MinioService;
import com.srs.live.common.util.SnowflakeIdGenerator;
import com.srs.live.entity.LiveParticipant;
import com.srs.live.entity.LiveRecord;
import com.srs.live.mapper.LiveParticipantMapper;
import com.srs.live.mapper.LiveRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LiveRecordService {

    private final LiveRecordMapper liveRecordMapper;
    private final LiveParticipantMapper liveParticipantMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final MinioService minioService;

    public LiveRecordService(LiveRecordMapper liveRecordMapper,
                             LiveParticipantMapper liveParticipantMapper,
                             SnowflakeIdGenerator idGenerator,
                             MinioService minioService) {
        this.liveRecordMapper = liveRecordMapper;
        this.liveParticipantMapper = liveParticipantMapper;
        this.idGenerator = idGenerator;
        this.minioService = minioService;
    }

    /** 创建直播记录（开播时调用） */
    @Transactional
    public LiveRecord createRecord(String roomId, String publisherUid, boolean recordingEnabled) {
        LiveRecord record = new LiveRecord();
        record.setId(idGenerator.nextId());
        record.setRoomId(roomId);
        record.setPublisherUid(publisherUid);
        record.setStartedAt(LocalDateTime.now());
        record.setRecordingEnabled(recordingEnabled ? 1 : 0);
        record.setRecordingStatus(recordingEnabled ? "recording" : "none");
        record.setParticipantCount(0);
        liveRecordMapper.insert(record);
        log.info("live record created: id={}, roomId={}, recording={}", record.getId(), roomId, recordingEnabled);
        return record;
    }

    /** 结束直播记录（关播时调用） */
    @Transactional
    public void finishRecord(Long recordId) {
        LiveRecord record = liveRecordMapper.selectById(recordId);
        if (record == null) return;

        LocalDateTime now = LocalDateTime.now();
        record.setEndedAt(now);
        record.setDurationSeconds((int) Duration.between(record.getStartedAt(), now).getSeconds());

        // 统计参与人数
        Long count = liveParticipantMapper.selectCount(
                new LambdaQueryWrapper<LiveParticipant>().eq(LiveParticipant::getLiveRecordId, recordId));
        record.setParticipantCount(count != null ? count.intValue() : 0);

        // 如果开启了录播，触发上传
        if (record.getRecordingEnabled() == 1) {
            record.setRecordingStatus("uploading");
            liveRecordMapper.updateById(record);
            triggerRecordingUpload(record);
        } else {
            liveRecordMapper.updateById(record);
        }
        log.info("live record finished: id={}, duration={}s, participants={}", recordId, record.getDurationSeconds(), record.getParticipantCount());
    }

    /** 参与者进入记录 */
    public void recordParticipantJoin(Long recordId, String uid, String username) {
        LiveParticipant p = new LiveParticipant();
        p.setLiveRecordId(recordId);
        p.setUid(uid);
        p.setUsername(username);
        p.setJoinedAt(LocalDateTime.now());
        liveParticipantMapper.insert(p);
    }

    /** 参与者离开记录 */
    public void recordParticipantLeave(Long recordId, String uid) {
        LiveParticipant p = liveParticipantMapper.selectOne(
                new LambdaQueryWrapper<LiveParticipant>()
                        .eq(LiveParticipant::getLiveRecordId, recordId)
                        .eq(LiveParticipant::getUid, uid)
                        .isNull(LiveParticipant::getLeftAt)
                        .last("LIMIT 1"));
        if (p != null) {
            LocalDateTime now = LocalDateTime.now();
            p.setLeftAt(now);
            p.setDurationSeconds((int) Duration.between(p.getJoinedAt(), now).getSeconds());
            liveParticipantMapper.updateById(p);
        }
    }

    /** 通过ID获取直播记录 */
    public LiveRecord getRecordById(Long recordId) {
        return liveRecordMapper.selectById(recordId);
    }

    /** 获取当前活跃直播记录（room维度） */
    public LiveRecord getActiveRecord(String roomId) {
        return liveRecordMapper.selectOne(
                new LambdaQueryWrapper<LiveRecord>()
                        .eq(LiveRecord::getRoomId, roomId)
                        .isNull(LiveRecord::getEndedAt)
                        .orderByDesc(LiveRecord::getStartedAt)
                        .last("LIMIT 1"));
    }

    /** 获取直播参与者列表 */
    public List<LiveParticipant> getParticipants(Long recordId) {
        return liveParticipantMapper.selectList(
                new LambdaQueryWrapper<LiveParticipant>()
                        .eq(LiveParticipant::getLiveRecordId, recordId)
                        .orderByAsc(LiveParticipant::getJoinedAt));
    }

    /** 获取房间的直播历史 */
    public List<LiveRecord> getRoomRecords(String roomId) {
        return liveRecordMapper.selectList(
                new LambdaQueryWrapper<LiveRecord>()
                        .eq(LiveRecord::getRoomId, roomId)
                        .orderByDesc(LiveRecord::getStartedAt));
    }

    /** 触发录播文件上传（实际场景由SRS录播完成后回调触发） */
    private void triggerRecordingUpload(LiveRecord record) {
        try {
            // 模拟：SRS 录播本地文件路径
            String localFilePath = "/tmp/srs-recordings/" + record.getRoomId() + ".mp4";
            File localFile = new File(localFilePath);
            if (localFile.exists()) {
                String objectName = MinioService.buildRecordingPath(record.getRoomId(), record.getId());
                String url = minioService.uploadFile(objectName, localFile, "video/mp4");
                if (url != null) {
                    record.setRecordingStatus("done");
                    record.setRecordingUrl(url);
                    liveRecordMapper.updateById(record);
                    log.info("recording uploaded: recordId={}, url={}", record.getId(), url);
                } else {
                    record.setRecordingStatus("failed");
                    liveRecordMapper.updateById(record);
                }
            } else {
                log.warn("recording file not found: {}", localFilePath);
                record.setRecordingStatus("failed");
                liveRecordMapper.updateById(record);
            }
        } catch (Exception e) {
            log.error("recording upload failed: recordId={}", record.getId(), e);
            record.setRecordingStatus("failed");
            liveRecordMapper.updateById(record);
        }
    }
}