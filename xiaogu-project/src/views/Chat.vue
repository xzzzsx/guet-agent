<script>
// ... 其他导入保持不变

export default {
  // ... 其他代码保持不变
  setup() {
    const chatStore = useChatStore();
    const messages = ref([]);

    // 确保在组件挂载时加载当前会话消息
    // 在onMounted钩子中添加更多日志
    onMounted(async () => {
      console.log("[ChatView] 组件挂载，初始化ChatStore");
      
      // 初始化ChatStore（包括BroadcastChannel监听）
      try {
        await chatStore.init();
        console.log("[ChatView] ChatStore初始化完成，BroadcastChannel实例:", chatStore.broadcastChannel);
      } catch (error) {
        console.error("[ChatView] ChatStore初始化失败:", error);
      }

      // ... 其他代码保持不变
    });

    // ... 其他代码保持不变
  },
};
</script>