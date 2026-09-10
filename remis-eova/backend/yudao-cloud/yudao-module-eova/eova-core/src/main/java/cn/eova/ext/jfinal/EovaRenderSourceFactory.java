package cn.eova.ext.jfinal;

import com.jfinal.template.source.ClassPathSource;
import com.jfinal.template.source.FileSource;
import com.jfinal.template.source.ISource;
import com.jfinal.template.source.ISourceFactory;

/**
 * <p>ported from: cn.eova.ext.jfinal.EovaRenderSourceFactory
 * <br>source revision: meta-eova/eova 1b1d39e7350f7e031b216aad0399fc8cc55dce08
 * <br>本单元为逐行等价 port：文件体与旧实现逐字节一致，仅新增本追溯头。
 * <br><b>刻意保留的既有语义：</b>
 * <ol>
 *   <li>模板源工厂；仅依赖 enjoy 的 ISourceFactory/FileSource/ClassPathSource —— 逐字节</li>
 *   <li>模板查找顺序属契约（决定同名模板的优先级）</li>
 * </ol>
 */
/**
 *
 * EovaMeta 页面模版渲染组合策略
 * /eova 下资源渲染, 重置路径
 * @author Jieven
 */
public class EovaRenderSourceFactory implements ISourceFactory {

    public ISource getSource(String baseTemplatePath, String fileName, String encoding) {
        // System.out.println("EovaRenderSourceFactory:" + fileName);
        // Eova请求重置: /eova/x.html => classpath:/webapp/eova/x.html
        if (fileName.startsWith("/eova")) {
            /*
                自动追加根目录
                与Web容器配置保持 /eova/xxx 的访问逻辑
                undertow.resourcePath = classpath:webapp
             */
            fileName = "/webapp" + fileName;
            return new ClassPathSource(null, fileName, encoding);
        } else {
            // 其他请求再默认路径下访问
            return new FileSource(baseTemplatePath, fileName, encoding);
        }

    }

}