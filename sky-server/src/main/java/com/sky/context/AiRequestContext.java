package com.sky.context;

/**
 * 保存一次内部 AI 工具请求中已经验证过的用户身份。
 * 用户 ID 只允许由认证拦截器写入，Controller 不能从模型参数或普通请求参数读取。
 */
public final class AiRequestContext {

    /*
     * 这里使用 ThreadLocal 是为了让同一个同步 HTTP 请求中的 Controller、Service
     * 能共享已认证身份。它不是会话存储，也不能跨异步线程或跨服务传播。
     */
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private AiRequestContext() {
    }

    public static void set(Long userId, String conversationId, String traceId) {
        CONTEXT.set(new Context(userId, conversationId, traceId));
    }

    /** 当前工具调用代表的用户，由签名上下文中的 userId 得到。 */
    public static Long getCurrentUserId() {
        Context context = CONTEXT.get();
        return context == null ? null : context.userId;
    }

    /** AI 会话 ID，用于后续审计和串联一次客服会话。 */
    public static String getConversationId() {
        Context context = CONTEXT.get();
        return context == null ? null : context.conversationId;
    }

    /** 本次内部 HTTP 调用的追踪 ID，不等同于会话 ID。 */
    public static String getTraceId() {
        Context context = CONTEXT.get();
        return context == null ? null : context.traceId;
    }

    public static void clear() {
        // Tomcat 会复用工作线程，请求结束不清理会造成下一次请求继承错误用户身份。
        CONTEXT.remove();
    }

    private static final class Context {
        private final Long userId;
        private final String conversationId;
        private final String traceId;

        private Context(Long userId, String conversationId, String traceId) {
            this.userId = userId;
            this.conversationId = conversationId;
            this.traceId = traceId;
        }
    }
}
