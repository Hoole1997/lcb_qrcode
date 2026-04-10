package net.corekit.core.report

/**
 * 数据上报接口
 * 提供统一的数据上报功能
 */
interface ReporterData {
    /**
     * 获取上报器名称
     * @return 上报器名称
     */
    fun getName(): String
    
    /**
     * 上报数据
     * @param eventName 事件名称
     * @param data 数据Map，key为参数名，value为参数值
     */
    fun reportData(eventName: String, data: Map<String, Any>)
    
    /**
     * 设置公共参数
     * @param params 公共参数Map，key为参数名，value为参数值
     */
    fun setCommonParams(params: Map<String, Any>)
    
    /**
     * 设置用户参数
     * @param params 用户参数Map，key为参数名，value为参数值
     */
    fun setUserParams(params: Map<String, Any>)
}

/**
 * 数据上报管理器
 * 管理数据上报器的注入和调用
 */
object ReportDataManager {
    private var reporters: MutableList<ReporterData> = mutableListOf()

    /**
     * 设置数据上报器集合
     * @param reporters 数据上报器实现集合
     */
    fun setReporters(reporters: Collection<ReporterData>) {
        this.reporters.clear()
        this.reporters.addAll(reporters)
    }
    
    /**
     * 设置公共参数
     * @param params 公共参数Map
     */
    fun setCommonParams(params: Map<String, Any>) {
        // 同时设置所有上报器的公共参数
        reporters.forEach { reporter ->
            try {
                reporter.setCommonParams(params)
            } catch (e: Exception) {
                // 单个上报器失败不影响其他上报器
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 设置用户参数
     * @param params 用户参数Map
     */
    fun setUserParams(params: Map<String, Any>) {
        // 同时设置所有上报器的用户参数
        reporters.forEach { reporter ->
            try {
                reporter.setUserParams(params)
            } catch (e: Exception) {
                // 单个上报器失败不影响其他上报器
                e.printStackTrace()
            }
        }
    }
    
    /**
     * 上报数据（自动合并公共参数和用户参数）
     * @param eventName 事件名称
     * @param data 数据Map
     */
    fun reportData(eventName: String, data: Map<String, Any>) {
        try {
            // 遍历所有上报器进行上报
            reporters.forEach { reporter ->
                try {
                    reporter.reportData(eventName, data)
                } catch (e: Exception) {
                    // 单个上报器失败不影响其他上报器
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            // 静默处理异常，避免影响主流程
            e.printStackTrace()
        }
    }
    
    /**
     * 按名称上报数据到指定上报器
     * @param reporterName 上报器名称
     * @param eventName 事件名称
     * @param data 数据Map
     */
    fun reportDataByName(reporterName: String, eventName: String, data: Map<String, Any>) {
        try {
            // 查找指定名称的上报器
            val targetReporter = reporters.find { it.getName() == reporterName }
            if (targetReporter != null) {
                try {
                    targetReporter.reportData(eventName, data)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            // 静默处理异常，避免影响主流程
            e.printStackTrace()
        }
    }

    /**
     * 检查是否已初始化
     * @return 是否已设置上报器
     */
    fun isInitialized(): Boolean {
        return reporters.isNotEmpty()
    }
}
