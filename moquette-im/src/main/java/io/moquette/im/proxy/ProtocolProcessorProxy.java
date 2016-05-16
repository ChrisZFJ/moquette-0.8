package io.moquette.im.proxy;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

/**
 * ClassName: ProtocolProcessorProxy 
 * @Description: 对ProtocolProcessor对象进行代理,以实现IM的状态同步逻辑.
 * @author cuidonghuan
 * @date 2016年5月16日 下午4:57:52
 */

public class ProtocolProcessorProxy implements MethodInterceptor {
	
	private Enhancer enhancer = new Enhancer();
	
	/**
	 * 获得代理对象
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
		
		// 这里对ProtocolProcessor的内部方法进行增强
		Object result = null;
		// 增强...
		result = proxy.invoke(object, args);
		
		return result;
	}

}
