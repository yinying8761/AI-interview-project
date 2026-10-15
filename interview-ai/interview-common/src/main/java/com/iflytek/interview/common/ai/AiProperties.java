package com.iflytek.interview.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Python AI 服务连接配置。
 * 通过 application*.yml 的 app.ai.* 提供。
 */
@Data
@Component
@ConfigurationProperties("app.ai")
public class AiProperties {

    /** Python AI 服务基地址，例如 http://127.0.0.1:8765。 */
    private String baseUrl = "http://127.0.0.1:8765";


    /** 默认音色，用于 TTS。 */
    private String defaultVoice = "zh-CN-YunxiNeural";

    /** 连接超时（毫秒）。 */
    private int connectTimeout = 3000;

    /** 读取超时（毫秒）。AI 出题较慢，默认给足时间。 */
    private int readTimeout = 60000;
}
