package com.threathunter.nebula.onlineserver;

import com.threathunter.model.Event;
import com.threathunter.nebula.common.util.SystemClock;
import com.threathunter.nebula.testt.event.maker.HttpDynamicEventMaker;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Created by daisy on 17/4/24.
 */
public class DelayQueueTest {
    @Test
    public void testDelay() throws InterruptedException {
        HttpDynamicEventMaker maker = new HttpDynamicEventMaker(1);
        DelayQueue<DelayEvent> queue = new DelayQueue<>();
        Event event = maker.nextEvent();
        event.setTimestamp(System.currentTimeMillis() + 10000);
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (Exception e) {
                ;
            }
            queue.offer(new DelayEvent(event, "check"));
        });
//        queue.offer(new DelayEvent(event, "check"));
        Thread.sleep(1000);
        thread.start();
        Assert.assertNotNull(queue.take());
    }

    static class DelayEvent implements Delayed {

        private final Event event;
        private final String checkString;

        public DelayEvent(final Event event, final String checkString) {
            this.event = event;
            this.checkString = checkString;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(this.event.getTimestamp() - SystemClock.getCurrentTimestamp(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(final Delayed o) {
            return (int) (this.event.getTimestamp() - ((DelayEvent) o).event.getTimestamp());
        }
    }
}
