package io.moquette.im.proxy;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * ClassName: ServerProxy 
 * @Description: Server代理类，增强Server功能
 * @author cuidonghuan
 * @date 2016年5月16日 下午4:50:26
 */

public class ServerProxy implements MethodInterceptor {
	
	private Enhancer enhancer = new Enhancer();
	
	/**
	 * 获取代理对象
	 */
	public Object getProxy(Class<?> clazz) {
		// 设置需要创建子类的类 
		enhancer.setSuperclass(clazz);
		enhancer.setCallback(this);
		
		// 通过字节码技术动态创建子类实例  
		return enhancer.create();
	}

	@Override
	public Object intercept(Object object, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {
		
		// 这里执行增强处理
		Object result = null;
		result = proxy.invoke(object, args);
		
		return result;
	}

}
