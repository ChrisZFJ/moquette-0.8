package io.moquette.im.callback;

/**
 * ClassName: ICallback 
 * @Description: 回调接口基类
 * @author cuidonghuan
 * @date 2016年5月19日 上午8:13:56
 */

public interface ICallback {
	
	/**
	 * @Description: 当检测到指定事件发生时，通知相关子类执行逻辑
	 * @param object 传递的对象  
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午8:24:18
	 */
	public void notify(Object object);
}
