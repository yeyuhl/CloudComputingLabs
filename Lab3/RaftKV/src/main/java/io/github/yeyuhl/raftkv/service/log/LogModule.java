package io.github.yeyuhl.raftkv.service.log;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.File;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志模块
 *
 * @author yeyuhl
 * @since 2023/8/19
 */
@Setter
@Getter
@Slf4j
public class LogModule {
    public String dbPath;
    public String logsPath;

    private RocksDB logDb;
    /**
     * 用LAST_INDEX_KEY来获取对应的index
     */
    public final static byte[] LAST_INDEX_KEY = "LAST_INDEX_KEY".getBytes();
    ReentrantLock lock = new ReentrantLock();

    private LogModule() {
        if (dbPath == null) {
            dbPath = "./rocksDB-raft/" + System.getProperty("server.port");
        }
        if (logsPath == null) {
            logsPath = dbPath + "/logModule";
        }

        RocksDB.loadLibrary();
        Options options = new Options();
        options.setCreateIfMissing(true);
        File file = new File(logsPath);
        boolean success = false;
        if (!file.exists()) {
            // 创建日志目录
            success = file.mkdirs();
        }
        if (success) {
            log.warn("make a new dir : " + logsPath);
        }
        try {
            // 使用RocksDB打开日志文件
            logDb = RocksDB.open(options, logsPath);
        } catch (RocksDBException e) {
            log.warn(e.getMessage());
        }
    }

    public static LogModule getInstance() {
        return LogModuleLazyHolder.INSTANCE;
    }

    private static class LogModuleLazyHolder {
        private static final LogModule INSTANCE = new LogModule();
    }

    public void destroy() throws Throwable {
        logDb.close();
        log.info("destroy success");
    }

    /**
     * 将logEntry添加到日志文件的尾部
     * logEntry的index为key（需要保证递增），logEntry序列化后作为value存入
     */
    public void write(LogEntry logEntry) {
        boolean success = false;
        try {
            lock.lock();
            logEntry.setIndex(getLastIndex() + 1);
            logDb.put(logEntry.getIndex().toString().getBytes(), JSON.toJSONBytes(logEntry));
            success = true;
            log.info("write logEntry success, logEntry info: [{}]", logEntry);
        } catch (RocksDBException e) {
            log.warn(e.getMessage());
        } finally {
            if (success) {
                // 更新lastIndex
                updateLastIndex(logEntry.getIndex());
            }
            lock.unlock();
        }
    }

    /**
     * 读取index对应的日志条目
     */
    public LogEntry read(Long index) {
        try {
            byte[] result = logDb.get(index.toString().getBytes());
            if (result == null) {
                return null;
            }
            // 将byte[]反序列化为LogEntry对象
            return JSON.parseObject(result, LogEntry.class);
        } catch (RocksDBException e) {
            log.warn(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 删除index在[startIndex, lastIndex]这个区间内的日志
     */
    public void removeOnStartIndex(Long startIndex) {
        boolean success = false;
        int count = 0;
        try {
            lock.lock();
            for (Long i = startIndex; i <= getLastIndex(); i++) {
                logDb.delete(i.toString().getBytes());
                ++count;
            }
            success = true;
            log.warn("rocksDB removeOnStartIndex success, count={} startIndex={}, lastIndex={}", count, startIndex, getLastIndex());
        } catch (RocksDBException e) {
            log.warn(e.getMessage(), e);
        } finally {
            if (success) {
                // 更新lastIndex
                updateLastIndex(getLastIndex() - count);
            }
            lock.unlock();
        }
    }

    /**
     * 获取最后一条日志
     */
    public LogEntry getLast() {
        try {
            byte[] result = logDb.get(convert(getLastIndex()));
            if (result == null) {
                return null;
            }
            return JSON.parseObject(result, LogEntry.class);
        } catch (RocksDBException e) {
            log.error("RocksDB getLast error", e);
        }
        return null;
    }

    /**
     * 获取最后一条日志的index，如果没有日志则返回-1
     */
    public Long getLastIndex() {
        byte[] lastIndex = "-1".getBytes();
        try {
            lastIndex = logDb.get(LAST_INDEX_KEY);
            if (lastIndex == null) {
                lastIndex = "-1".getBytes();
            }
        } catch (RocksDBException e) {
            log.error("RocksDB getLastIndex error", e);
        }
        return Long.valueOf(new String(lastIndex));
    }

    private byte[] convert(Long index) {
        return index.toString().getBytes();
    }

    private void updateLastIndex(Long index) {
        try {
            logDb.put(LAST_INDEX_KEY, convert(index));
        } catch (RocksDBException e) {
            log.error("RocksDB updateLastIndex error", e);
        }
    }


}
