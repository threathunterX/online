//package com.threathunter.nebula.onlineserver;
//
//import com.threathunter.model.Event;
//import com.threathunter.platform.StrategyInfoCache;
//import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
//import org.junit.Assert;
//import org.junit.Before;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.mockito.Mockito;
//import org.powermock.api.mockito.PowerMockito;
//import org.powermock.core.classloader.annotations.PrepareForTest;
//import org.powermock.modules.junit4.PowerMockRunner;
//import org.powermock.reflect.Whitebox;
//
//import java.util.*;
//
///**
// * 
// */
//@RunWith(PowerMockRunner.class)
//@PrepareForTest({ProfileNoticeChecker.class, NoticeSender.class})
//public class NoticeManagerTest {
//    private static final String PROFILE_STRATEGY = "strategy_profile";
//    private static final String NORMAL_STRATEGY = "strategy_normal";
//
//    @Before
//    public void setUp() {
//        Set<String> strategyToProfile = new HashSet<>();
//        strategyToProfile.add(PROFILE_STRATEGY);
//
//        initialStrategyCache();
//
//        Map<String, List<String>> strategyDimensionInfo = new HashMap<>();
//        strategyDimensionInfo.put(NORMAL_STRATEGY, new ArrayList<>());
//        strategyDimensionInfo.put(PROFILE_STRATEGY, new ArrayList<>());
//        strategyDimensionInfo.get(NORMAL_STRATEGY).add(String.format("%s@@%s", NORMAL_STRATEGY, "ip"));
//        strategyDimensionInfo.get(NORMAL_STRATEGY).add(String.format("%s@@%s", NORMAL_STRATEGY, "did"));
//        strategyDimensionInfo.get(PROFILE_STRATEGY).add(String.format("%s@@%s", PROFILE_STRATEGY, "user"));
//        strategyDimensionInfo.get(PROFILE_STRATEGY).add(String.format("%s@@%s", PROFILE_STRATEGY, "did"));
//        NoticeManager.getInstance().setStrategyDimensionInfo(strategyDimensionInfo);
//    }
//
//    @Test
//    public void testSetStrategies() throws Exception {
//        PowerMockito.mockStatic(NoticeSender.class);
//        ProfileNoticeChecker profileNoticeChecker = PowerMockito.mock(ProfileNoticeChecker.class);
//        NoticeSender noticeSender = PowerMockito.mock(NoticeSender.class);
//        Whitebox.setInternalState(ProfileNoticeChecker.class, "INSTANCE", profileNoticeChecker);
//        PowerMockito.when(NoticeSender.getInstance()).thenReturn(noticeSender);
//
//        HttpDynamicEventMaker maker = new HttpDynamicEventMaker(1);
//        Event event = maker.nextEvent();
//        Set<String> dimensionStrategies = new HashSet<>();
//        dimensionStrategies.add(String.format("%s@@%s", NORMAL_STRATEGY, "ip"));
//        dimensionStrategies.add(String.format("%s@@%s", NORMAL_STRATEGY, "did"));
//        NoticeManager.getInstance().dealWithStrategies(event, dimensionStrategies);
//
//        PowerMockito.verifyZeroInteractions(profileNoticeChecker);
//        Mockito.verify(noticeSender).sendNotice(Mockito.isA(Event.class));
//
//        Assert.assertEquals(1, ((Collection) event.getPropertyValues().getOrDefault("noticelist", new ArrayList<>())).size());
//        Assert.assertEquals(0, ((Collection) event.getPropertyValues().getOrDefault("strategylist", new ArrayList<>())).size());
//
//        Event event2 = maker.nextEvent();
//        Set<String> dimensionStrategyProfile = new HashSet<>();
//        dimensionStrategyProfile.add(String.format("%s@@%s", PROFILE_STRATEGY, "user"));
//        dimensionStrategyProfile.add(String.format("%s@@%s", PROFILE_STRATEGY, "did"));
//        NoticeManager.getInstance().dealWithStrategies(event2, dimensionStrategyProfile);
//
//        PowerMockito.verifyZeroInteractions(noticeSender);
//        Mockito.verify(profileNoticeChecker).addEvent(Mockito.isA(Event.class));
//
//        Assert.assertEquals(0, ((Collection) event2.getPropertyValues().getOrDefault("noticelist", new ArrayList<>())).size());
//        Assert.assertEquals(1, ((Collection) event2.getPropertyValues().getOrDefault("strategylist", new ArrayList<>())).size());
//
//        Event event3 = maker.nextEvent();
//        Set<String> dimensionStrategyNone = new HashSet<>();
//        dimensionStrategyProfile.add(String.format("%s@@%s", "none", "user"));
//        dimensionStrategyProfile.add(String.format("%s@@%s", "none", "did"));
//        NoticeManager.getInstance().dealWithStrategies(event3, dimensionStrategyNone);
//
//        PowerMockito.verifyZeroInteractions(noticeSender);
//        PowerMockito.verifyZeroInteractions(profileNoticeChecker);
//    }
//
//    private void initialStrategyCache() {
//        List<Map<String, Object>> arrayList = new ArrayList<>();
//        arrayList.add(buildStrategyInfo(NORMAL_STRATEGY));
//        Map<String, Object> profileStrategyInfo = buildStrategyInfo(PROFILE_STRATEGY);
//        profileStrategyInfo.put("scope", "profile");
//        arrayList.add(profileStrategyInfo);
//
//        StrategyInfoCache.getInstance().update(arrayList);
//    }
//
//    private Map<String, Object> buildStrategyInfo(String name) {
//        Map<String, Object> map = new HashMap<>();
//        map.put("category", "");
//        map.put("score", 10);
//        map.put("tags", new ArrayList<>());
//        map.put("scope", "realtime");
//        map.put("expire", 300);
//        map.put("name", name);
//
//        return map;
//    }
//}
