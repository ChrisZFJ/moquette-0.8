package io.moquette.im.processor;

import io.moquette.im.callback.IPersistCallback;

/**
 * ClassName: PersistProcessor 
 * @Description: 持久化消息对象处理器
 * @author cuidonghuan
 * @date 2016年5月19日 上午10:02:48
 */

public class PersistProcessor {

	/** PersistProcessor持有IPersistCallback子类的引用，用于回调传递消息对象用于存储 */
	private final IPersistCallback callback;
	
	/** PersistProcessor单例对象 */
	private static PersistProcessor instance = null;
	
	private PersistProcessor(IPersistCallback callback) {
		this.callback = callback;
	}
	
	/**
	 * @Description: 获得PersistProcessor的单例对象 
	 * @param callback IPersistCallback的子类对象
	 * @return PersistProcessor单例对象  
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午10:05:49
	 */
	public static PersistProcessor getInstance(IPersistCallback callback) {
		if(null == instance && callback != null) {
			instance = new PersistProcessor(callback);
		}
		
		return instance;
	}
	
	/**
	 * @Description: 持久化消息对象时调用
	 * @param payload 需要被持久化的消息对象的byte[]形式
	 * @return void  
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午10:05:49
	 */
	public void onPersist(byte[] payload) {
		callback.onPersist(payload);
	}
}
