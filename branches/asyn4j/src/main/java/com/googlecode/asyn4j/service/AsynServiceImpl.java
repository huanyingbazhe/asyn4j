package com.googlecode.asyn4j.service;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.googlecode.asyn4j.core.AsynWorkExecute;
import com.googlecode.asyn4j.core.result.AsynResult;
import com.googlecode.asyn4j.core.result.AsynResultCacheService;
import com.googlecode.asyn4j.core.result.AsynResultCachedServiceImpl;
import com.googlecode.asyn4j.core.work.AsynWork;
import com.googlecode.asyn4j.core.work.AsynWorkCachedService;
import com.googlecode.asyn4j.core.work.AsynWorkCachedServiceImpl;
import com.googlecode.asyn4j.core.work.AsynWorkEntity;
import com.googlecode.asyn4j.util.AsynSpringUtil;

@SuppressWarnings("unchecked")
public class AsynServiceImpl implements AsynService {

	private static Log log = LogFactory.getLog(AsynServiceImpl.class);

	private static AsynWorkCachedService anycWorkCachedService = null;

	// default add work wait time
	private static long addWorkWaitTime = Long.MAX_VALUE;

	// default work queue length
	private static int workQueueLength = Integer.MAX_VALUE;

	private static final int DEFAULT_WORK_WEIGHT = 5;

	private final static int CPU_NUMBER = Runtime.getRuntime()
			.availableProcessors();

	private static Executor executor = null;

	private static boolean run = false;

	

	private static BlockingQueue<Future<AsynResult>> resultBlockingQueue = null;

	private static Map<String, Integer> statMap = new HashMap<String, Integer>(
			3);

	private static StringBuilder infoSb = new StringBuilder();

	private AsynWorkExecute asynWorkExecute = null;

	private AsynResultCachedServiceImpl asynResultCacheService = null;

	public AsynServiceImpl(ExecutorService executorservice) {
		init(executorservice, workQueueLength, addWorkWaitTime);
	}

	public AsynServiceImpl() {
		init(null, workQueueLength, addWorkWaitTime);
	}

	public AsynServiceImpl(ExecutorService executorservice, int workQueueLength,
			long addWorkWaitTime) {
		init(executorservice, workQueueLength, addWorkWaitTime);
	}

	/**
	 * init Asyn Service
	 */
	private void init(ExecutorService executorservice, int workQueueLength,
			long addWorkWaitTime) {

		if (!run) {
			if (executor == null) {
				 executor = Executors.newCachedThreadPool();
			}

			// init work execute server
			anycWorkCachedService = new AsynWorkCachedServiceImpl(
					workQueueLength, addWorkWaitTime);

			// init work execute queue
			resultBlockingQueue = new LinkedBlockingQueue<Future<AsynResult>>(
					workQueueLength);

			

			// start work thread
			asynWorkExecute = new AsynWorkExecute(anycWorkCachedService,
					executorservice,resultBlockingQueue);
			executor.execute(asynWorkExecute);

			// start callback thread
			asynResultCacheService = new AsynResultCachedServiceImpl(
					resultBlockingQueue, executor);
			executor.execute(asynResultCacheService);
			run = true;
		}

	}

	@Override
	public void addWork(Object[] params, Class clzss, String method,
			AsynResult anycResult) {

		this.addWork(params, clzss, method, anycResult, DEFAULT_WORK_WEIGHT);

	}

	@Override
	public void addWorkWithSpring(Object[] params, String target,
			String method, AsynResult anycResult) {
		this.addWorkWithSpring(params, target, method, anycResult,
				DEFAULT_WORK_WEIGHT);

	}

	@Override
	public void addWork(Object[] params, Class clzss, String method,
			AsynResult anycResult, int weight) {
		Object target = null;

		try {
			Constructor constructor = clzss.getConstructor();
			if (constructor == null) {
				throw new IllegalArgumentException(
						"target not have default constructor function");
			}
			// Instance target object
			target = clzss.newInstance();
		} catch (Exception e) {
			log.error(e);
			return;
		}

		AsynWork anycWork = new AsynWorkEntity(target, method, params,
				anycResult);

		anycWork.setWeight(weight);

		addAsynWork(anycWork);

	}

	@Override
	public void addWorkWithSpring(Object[] params, String target,
			String method, AsynResult anycResult, int weight) {
		// get spring bean
		Object bean = AsynSpringUtil.getBean(target);

		if (bean == null)
			throw new IllegalArgumentException("spring bean is null");

		AsynWork anycWork = new AsynWorkEntity(bean, method, params, anycResult);

		anycWork.setWeight(weight);

		addAsynWork(anycWork);

	}

	/**
	 * add asyn work
	 * 
	 * @param asynWork
	 */
	public void addAsynWork(AsynWork asynWork) {
		if (asynWork.getWeight() <= 0) {
			asynWork.setWeight(DEFAULT_WORK_WEIGHT);
		}
		anycWorkCachedService.addWork(asynWork);
	}

	@Override
	public Map<String, Integer> getRunStatMap() {
		if (run) {
			statMap.clear();
			statMap.put("total", anycWorkCachedService.getTotalWork());
			statMap.put("execute", asynWorkExecute.getExecuteWork());
			statMap.put("callback", asynResultCacheService.getResultBack());
		}
		return statMap;
	}

	@Override
	public String getRunStatInfo() {
		if (run) {
			infoSb.delete(0, infoSb.length());
			infoSb.append("total asyn work:").append(
					anycWorkCachedService.getTotalWork()).append("\t");
			infoSb.append(",excute asyn work:").append(
					asynWorkExecute.getExecuteWork()).append("\t");
			infoSb.append(",callback asyn result:").append(
					asynResultCacheService.getResultBack()).append("\t");
		}
		return infoSb.toString();
	}

}
