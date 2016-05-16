package io.moquette.im.proxy;

/**
 * MQTT IM Server实现类
 * ClassName: IMServer 
 * @Description: 
 * @author cuidonghuan
 * @date 2016年5月16日 下午5:01:51
 */

public class IMServer {
	
	public IMServer() {}
	
	public void startIMServer(Class<?> clazz) {
		System.out.println("start im srever...");
		
		// 启动Moquette MQTT Server...
		startServer(clazz);
	}
	
	/**
	 * 启动Moquette MQTT Server
	 */
	private void startServer(Class<?> clazz) {
		// 启动Server
	}

}
