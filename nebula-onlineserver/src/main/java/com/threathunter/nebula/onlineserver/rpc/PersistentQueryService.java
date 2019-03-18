package com.threathunter.nebula.onlineserver.rpc;

import com.threathunter.babel.meta.ServiceMeta;
import com.threathunter.babel.meta.ServiceMetaUtil;
import com.threathunter.babel.rpc.Service;
import com.threathunter.babel.rpc.ServiceClient;
import com.threathunter.babel.rpc.impl.ServiceClientImpl;
import com.threathunter.config.CommonDynamicConfig;
import com.threathunter.model.Event;
import com.threathunter.model.EventMeta;
import com.threathunter.persistent.core.EventArgumentErrorType;
import com.threathunter.persistent.core.QueryActionTask;
import com.threathunter.persistent.core.api.QueryActionTaskManager;
import com.threathunter.persistent.core.api.QueryActionType;
import com.threathunter.persistent.core.util.EventUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 
 */
public class PersistentQueryService implements Service {

  private static final Logger logger = LoggerFactory.getLogger(PersistentQueryService.class);
  private static final long HOUR_MILLIS = 3600 * 1000;
  private final ServiceMeta queryServiceMeta;
  private final ServiceMeta responseServiceMeta;
  private final ServiceClient responseServiceClient;
  private final String fileDir = CommonDynamicConfig
      .getInstance().getString("events_query_result_dir", "./");

  public PersistentQueryService() {
    if (CommonDynamicConfig.getInstance().getString("babel_server", "redis").equals("redis")) {
      this.queryServiceMeta = ServiceMetaUtil
          .getMetaFromResourceFile("PersistentQuery_redis.service");
      this.responseServiceMeta = ServiceMetaUtil
          .getMetaFromResourceFile("PersistentQueryNotify_redis.service");
    } else {
      this.queryServiceMeta = ServiceMetaUtil
          .getMetaFromResourceFile("PersistentQuery_rmq.service");
      this.responseServiceMeta = ServiceMetaUtil
          .getMetaFromResourceFile("PersistentQueryNotify_rmq.service");
    }
    this.responseServiceClient = new ServiceClientImpl(this.responseServiceMeta,
        "presistentqueryresponse");
    QueryActionTaskManager manager = QueryActionTaskManager.getInstance();
    ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
    this.responseServiceClient.start();
    logger.warn("persistent query service start");
    service.scheduleWithFixedDelay(() -> {
      Map<String, QueryActionTask> tasks = manager.getTasks();
      Event event = EventUtil.createProgressNotifyEvent(tasks);
      if (tasks.size() > 0) {
        try {
          logger.warn(">>>>>>>persistent query notify: requestIDs = {}", tasks.keySet());
          logger.warn(">>>>>>>persistent query notify event = {}", event);
          this.responseServiceClient.notify(event, this.responseServiceMeta.getName());
        } catch (Exception e) {
          logger.error("send error", e);
        }
      }
    }, 0, 4, TimeUnit.SECONDS);
  }

  @Override
  public Event process(final Event event) {

    Event responseEvent = null;
    try {
        System.out.println("request event = " + event.toString());
        logger.warn("<<<<<<persistent query request event {}.======", event);
        EventArgumentErrorType argumentErrorType = EventUtil.checkEventArgument(event);
        String requestId = EventUtil.getRequestId(event);
        logger.warn("process persistent query with requestId {}.", requestId);
        System.out.println("process persistent query with requestId " + requestId);

      if (argumentErrorType.isValid()) {
        QueryActionType actionType = EventUtil.getActionType(event);
        if (actionType == QueryActionType.FETCH) {
          responseEvent = EventUtil.createFetchResponseEvent(requestId, event);
        }else {
          QueryActionTaskManager manager = QueryActionTaskManager.getInstance();
          boolean result = manager.executeActionTask(event, actionType);
          responseEvent = EventUtil.createResponseEvent(requestId, result, actionType);
        }
      } else {
        responseEvent = EventUtil.createResponseEvent(requestId, argumentErrorType);
      }
      System.out.println("response event = " + responseEvent.toString());
      logger.warn("======response event = {}.=======" , responseEvent.toString());
    } catch (Exception ex) {
      ex.printStackTrace();
      logger.error("something wrong in process.", ex);
    }
    return responseEvent;
  }


  @Override
  public ServiceMeta getServiceMeta() {
    return this.queryServiceMeta;
  }

  @Override
  public EventMeta getRequestEventMeta() {
    return null;
  }

  @Override
  public EventMeta getResponseEventMeta() {
    return null;
  }

  @Override
  public void close() {
    this.responseServiceClient.stop();
  }


}
