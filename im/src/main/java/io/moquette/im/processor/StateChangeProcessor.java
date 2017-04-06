package io.moquette.im.processor;

import io.moquette.im.callback.IStateChangeCallback;

/**
 * ClassName: StateChangeProcessor 
 * @Description: IM状态变化处理器
 * @author cuidonghuan
 * @date 2016年5月19日 上午9:43:30
 */

public class StateChangeProcessor {

	/** StateChangeProcessor持有IStateChangeCallback子类的引用，用于回调传递IM状态改变用户的信息 */
	private final IStateChangeCallback callback;
	
	/** StateChangeProcessor单例对象 */
	private static StateChangeProcessor instance = null;
	
	private StateChangeProcessor(IStateChangeCallback callback) {
		this.callback = callback;
	}
	
	/**
	 * @Description: 获得StateChangeProcessor的单例对象 
	 * @param callback IStateChangeCallback的子类对象
	 * @return StateChangeProcessor单例对象  
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午9:59:03
	 */
	public static StateChangeProcessor getInstance(IStateChangeCallback callback) {
		if(null == instance && callback != null) {
			instance = new StateChangeProcessor(callback);
		}
		
		return instance;
	}
	
	/**
	 * @Description: 用户上线时调用，传递上线用户的信息
	 * @param cliendId 上线用户clientId
	 * @return void  
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午10:00:20
	 */
	public void onOnline(String cliendId) {
		
		System.out.println("[moquette-im] [StateChangeProcessor] clientId " + cliendId + "上线!");
		callback.onOnLine(cliendId);
	}
	
	/**
	 * @Description: 用户离线时调用，传递离线用户的信息
	 * @param clientId 离线用户clientId
	 * @return void  
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午10:00:20
	 */
	public void onOffline(String clientId) {
		System.out.println("[moquette-im] [StateChangeProcessor] clientId " + clientId + "离线!");
		callback.onOffline(clientId);
	}
}
