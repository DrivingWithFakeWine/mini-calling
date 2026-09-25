package com.dking.mini_calling.common;
import java.lang.annotation.*;
/*

典型用途：健康检查接口、文件下载、导出 Excel、第三方回调——
这些接口的响应格式由外部协议决定，不能强套 {code, message, data}
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IgnoreWrap {
}

