package io.moquette.im.callback;

/**
 * ClassName: IPersistCallback 
 * @Description: 持久化消息回调接口
 * @author cuidonghuan
 * @date 2016年5月19日 上午9:36:53
 */

public interface IPersistCallback extends ICallback {

	/**
	 * @Description: 持久化消息时，回调该方法
	 * @param payload 消息对象byte[]形式
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午9:39:52
	 */
	public void onPersist(byte[] payload);
}
