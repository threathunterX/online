//package com.threathunter.nebula.onlineserver;
//
//import com.threathunter.config.CommonDynamicConfig;
//import com.threathunter.platform.persistent.EventPersistCommon;
//import com.threathunter.platform.util.PathHelper;
//import com.google.common.cache.Cache;
//import com.google.common.cache.CacheBuilder;
//import org.joda.time.DateTime;
//import org.junit.Test;
//
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.FileVisitResult;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.SimpleFileVisitor;
//import java.nio.file.attribute.BasicFileAttributes;
//import java.util.concurrent.TimeUnit;
//
//import static java.nio.file.FileVisitResult.CONTINUE;
//import static java.nio.file.FileVisitResult.TERMINATE;
//
///**
// * 
// */
//public class OfflineHelper {
//    public static String ensureAndGetHourPersistDir(long currentTimestamp) {
//        String currentDir = CommonDynamicConfig.getInstance().getString("persist_path",
//                String.format("%s/persistent", PathHelper.getModulePath()));
//        EventPersistCommon.ensure_dir(currentDir);
//        String dir = String.format("%s/%s", currentDir, new DateTime(currentTimestamp).toString("yyyyMMddHH"));
//        EventPersistCommon.ensure_dir(dir);
//        return dir;
//    }
//
//    public static String getCurrentHourPersistDir() {
//        String currentDir = CommonDynamicConfig.getInstance().getString("persist_path",
//                String.format("%s/persistent", PathHelper.getModulePath()));
//        EventPersistCommon.ensure_dir(currentDir);
//        return String.format("%s/%s", currentDir, new DateTime(System.currentTimeMillis()).toString("yyyyMMddHH"));
//    }
//
//    public static void deleteFileOrFolder(final String pathString) throws IOException {
//        File file = new File(pathString);
//        if (file.exists()) {
//            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>(){
//                @Override public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
//                        throws IOException {
//                    Files.delete(file);
//                    return CONTINUE;
//                }
//
//                @Override public FileVisitResult visitFileFailed(final Path file, final IOException e) {
//                    return handleException(e);
//                }
//
//                private FileVisitResult handleException(final IOException e) {
//                    e.printStackTrace(); // replace with more robust error handling
//                    return TERMINATE;
//                }
//
//                @Override public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
//                        throws IOException {
//                    if(e != null) return handleException(e);
//                    Files.delete(dir);
//                    return CONTINUE;
//                }
//            });
//        }
//    }
//
//    @Test
//    public void testCache() throws InterruptedException {
//        Cache<String, String> cache = CacheBuilder.newBuilder().expireAfterAccess(2, TimeUnit.SECONDS).removalListener((notification) -> {
//            System.out.println("remove: " + notification.getValue());
//        }).build();
//
//        cache.put("key1", "value1");
//        cache.put("key2", "value2");
//        Thread.sleep(3000);
//        cache.put("key3", "value3");
//        cache.put("key4", "value4");
//        cache.put("key4", "value5");
//        cache.invalidateAll();
//        System.out.println("invalidated all");
//        cache.invalidateAll();
//        cache.put("key5", "value5");
//        System.out.println("invalidated");
//        cache.invalidateAll();
//    }
//}
