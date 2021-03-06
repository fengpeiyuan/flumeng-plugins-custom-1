package com.flumeng.plugins.sink;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class Push2QueueSink extends AbstractSink implements Configurable {
		  private Logger logger = LoggerFactory.getLogger(Push2QueueSink.class);
		  private Jedis jedis;
		  private String host;
		  private int port;
		  private String queueName;
		  private int timeout;
		  private String password;
		  private int database;
		  private String charset;

		  @Override
		  public void configure(Context context) {
		    host = context.getString("host", "localhost");
		    port = context.getInteger("port", 6379);
		    queueName = context.getString("name");
		    timeout = context.getInteger("timeout", 2000);
		    password = context.getString("password", "");
		    database = context.getInteger("database", 0);
		    charset = context.getString("charset", "utf-8");
		    if (queueName == null) { throw new RuntimeException("Redis pushListName must be set."); }
		    logger.info("Flume Redis List Sink Configured");
		  }

		  @Override
		  public synchronized void start() {
		    jedis = new Jedis(host, port, timeout);
		    if (!"".equals(password)) {
		      jedis.auth(password);
		    }
		    if (database != 0) {
		      jedis.select(database);
		    }
		    logger.info("Redis Connected. (host: " + host + ", port: " + String.valueOf(port)+ ", timeout: " + String.valueOf(timeout) + ")");
		    super.start();
		  }

		  @Override
		  public synchronized void stop() {
		    jedis.disconnect();
		    super.stop();
		  }

		  @Override
		  public Status process() throws EventDeliveryException {
		    Status status = Status.READY;
		    Channel channel = getChannel();
		    Transaction transaction = channel.getTransaction();
		    try {
		      transaction.begin();
		      Event event = channel.take();
		      if (jedis.lpush(queueName, new String(event.getBody(), charset)) >= 0) {
		        transaction.commit();
		      } 
		    } catch (Throwable e) {
		      transaction.rollback();
		      status = Status.BACKOFF;
		      if (e instanceof Error) {
		        throw (Error) e;
		      }
		    } finally {
		      transaction.close();
		    }

		    return status;
		  }
}
