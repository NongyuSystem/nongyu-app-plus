package com.nongyu.appplus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nongyu.appplus.ui.NongyuApp
import com.nongyu.appplus.ui.theme.NongyuAppPlusTheme

/**
 * 应用唯一入口 Activity。
 *
 * 骨架阶段只负责挂载 Compose 根节点，不承载业务逻辑；
 * 后续路由、依赖注入等能力均在 [NongyuApp] 内部扩展。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NongyuAppPlusTheme {
                NongyuApp()
            }
        }
    }
}
