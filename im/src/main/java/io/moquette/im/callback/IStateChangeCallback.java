package io.moquette.im.callback;


/**
 * ClassName: IStateChangeCallback 
 * @Description: 与IM状态相关的回调接口
 * @author cuidonghuan
 * @date 2016年5月19日 上午8:28:40
 */

public interface IStateChangeCallback extends ICallback {

	/**
	 * @Description: 用户离线时，回调该方法
	 * @param clientId 离线用户clientId
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午8:30:32
	 */
	public void onOffline(String clientId);
	
	/**
	 * @Description: 用户上线时，回调该方法
	 * @param clientId 上线用户clientId
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午8:30:32
	 */
	public void onOnLine(String clientId);
}
