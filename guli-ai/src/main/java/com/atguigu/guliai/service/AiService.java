package com.atguigu.guliai.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.atguigu.common.core.domain.R;
import com.atguigu.common.core.domain.model.LoginUser;
import com.atguigu.common.utils.SecurityUtils;
import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.advisor.RecordOptimizationAdvisor;
import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.strategy.AiBean;
import com.atguigu.guliai.strategy.AiOperator;
import com.atguigu.guliai.strategy.OllamaAiOperator;
import com.atguigu.guliai.strategy.OpenAiOperator;
import com.atguigu.guliai.utils.FileUtil;
import com.atguigu.guliai.utils.MongoUtil;
import com.atguigu.guliai.vo.ChatVo;
import com.atguigu.guliai.vo.MessageVo;
import com.atguigu.guliai.vo.QueryVo;
import com.atguigu.system.domain.ChatKnowledge;
import com.atguigu.system.domain.ChatProject;
import com.atguigu.system.mapper.ChatKnowledgeMapper;
import com.atguigu.system.mapper.ChatProjectMapper;
import com.atguigu.guliai.constant.SystemConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AiService implements ApplicationContextAware, ApplicationListener<RecordOptimizationAdvisor.DeleteMessagesEvent> {

    @Autowired
    private ChatKnowledgeMapper chatKnowledgeMapper;

    @Autowired
    private ChatProjectMapper chatProjectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private List<String> sensitiveWords;

    @Autowired
    @Lazy // 添加延迟加载注解解决循环依赖
    private AgentCoordinatorService agentCoordinatorService; // 新增注入

    // 注入向量库（按项目类型选择）
    @Autowired
    private QdrantVectorStore openAiVectorStore;
    @Autowired
    private QdrantVectorStore ollamaVectorStore;

    private static final Map<String, AiOperator> MAP = new ConcurrentHashMap<>();



    /**
     * 统一获取spring容器中的AiOperator具体策略类,并放入map中方便切换
     *
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Object> beanMap = applicationContext.getBeansWithAnnotation(AiBean.class);
        if (CollectionUtils.isEmpty(beanMap)) {
            return;
        }
        Collection<Object> beans = beanMap.values();
        beans.forEach(bean -> {
            AiBean aiBean = bean.getClass().getAnnotation(AiBean.class);
            MAP.put(aiBean.value(), (AiOperator) bean);
        });
    }

    public AiOperator getAiOperator(String type) {
        return MAP.get(type);
    }

    /**
     * 获取知识库列表
     *
     * @param chatKnowledge
     * @param file
     */
    @Transactional
    public void upload(ChatKnowledge chatKnowledge, MultipartFile file) {
        //从文件中把内容取出来,通过工具类
        String content = FileUtil.getContentFromFile(file);

        //保存知识库到本地数据库(mysql)
        chatKnowledge.setFileName(file.getOriginalFilename());
        chatKnowledge.setContent(content);
        //获取登录用户信息
        LoginUser loginUser = SecurityUtils.getLoginUser();
        chatKnowledge.setUserId(loginUser.getUserId());
        chatKnowledge.setCreateBy(loginUser.getUsername());
        chatKnowledge.setCreateTime(new Date());
        this.chatKnowledgeMapper.insertChatKnowledge(chatKnowledge);

        //保存知识库到向量数据库:projectId knowledgeId content
        //根据projectId查询项目(模型的类型)
        ChatProject chatProject = this.chatProjectMapper.selectChatProjectByProjectId(chatKnowledge.getProjectId());

        //向向量数据库初始化知识库
        // 确保向量存储操作在事务范围内
        this.getAiOperator(chatProject.getType()).addDocs(chatKnowledge);
    }

    /**
     * 创建会话
     *
     * @param chatVo
     * @return
     */
    public String createChat(ChatVo chatVo) {
        Chat chat = new Chat();
        BeanUtils.copyProperties(chatVo, chat);
        //使用雪花算法生成唯一标识
        Snowflake snowflake = IdUtil.getSnowflake();
        Long chatId = snowflake.nextId();
        chat.setChatId(chatId);
        //创建时间
        chat.setCreateTime(new Date());
        //保存到MongoDB,并指定保存到MongoDB的哪个集合中
        this.mongoTemplate.insert(chat, MongoUtil.getChatCollectionName(chatVo.getProjectId()));
        return chatId.toString();
    }

    /**
     * 获取会话列表
     *
     * @param projectId
     * @param userId
     * @return
     */
    public List<Chat> listChat(Long projectId, Long userId) {
        // 根据projectId和userId进行查询，并且根据创建时间降序排列
        return this.mongoTemplate.find(Query.query(Criteria
                        .where("projectId").is(projectId)
                        .and("userId").is(userId)).with(Sort.by(Sort.Order.desc("createTime"))),
                Chat.class, MongoUtil.getChatCollectionName(projectId));
    }

    /**
     * 更新会话标题
     *
     * @param chatVo
     */
    public void updateChat(ChatVo chatVo) {
        //根据id更新会话标题
        this.mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(chatVo.getChatId())),
                Update.update("title", chatVo.getTitle()), MongoUtil.getChatCollectionName(chatVo.getProjectId()));
    }

    /**
     * 直接模型处理（移除内部的消息保存）
     */
    public Flux<String> directModelProcessing(QueryVo queryVo) {
        log.info("【直接模型处理】开始处理问题，绕过路由系统: {}", queryVo.getMsg());

        // 项目验证（复用原逻辑）
        if (queryVo.getProjectId() == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }
        ChatProject project = chatProjectMapper.selectChatProjectByProjectId(queryVo.getProjectId());
        if (project == null) {
            throw new RuntimeException("找不到ID为 " + queryVo.getProjectId() + " 的项目");
        }

        // +++ 移除原有的用户消息保存代码 +++

        return this.processChatRequest(queryVo, project.getType());
    }

    private Flux<String> processChatRequest(QueryVo queryVo, String modelType) {
        // +++ 此处已移除用户消息保存代码 +++

        // 仅保留以下逻辑：
        //2.查询本地知识库:Qdrant
        ChatProject chatProject = this.chatProjectMapper.selectChatProjectByProjectId(queryVo.getProjectId());
        if (chatProject == null) {
            throw new RuntimeException("对应的项目不存在!");
        }

        // 向量检索改为仅在 ChatClient + RetrievalAugmentationAdvisor 中执行，移除预检索

        //3.查询历史问答（包含刚保存的用户消息）
        String collectionName = MongoUtil.getMsgCollectionName(queryVo.getChatId());
        List<Message> messages = this.mongoTemplate.find(Query
                .query(Criteria.where("chatId").is(queryVo.getChatId()))
                .with(Sort.by(Sort.Order.asc("createTime"))), Message.class, collectionName);

        //组装上下文提示
        List<org.springframework.ai.chat.messages.Message> msgs = new ArrayList<>();
        String systemPrompt = "你是一个AI助手，负责回答用户问题。当需要查询课程信息时，必须使用提供的工具进行查询。所有工具调用必须包含projectId参数，其值为当前项目ID。";
        msgs.add(new SystemMessage(systemPrompt));
        if (!CollectionUtils.isEmpty(messages)) {//用户问答提示
            messages.forEach(m -> {
                org.springframework.ai.chat.messages.Message msg = null;
                if (m.getType().intValue() == 0) { //如果type为0,则说明是用户的提问UserMessage
                    msg = new UserMessage(m.getContent());
                } else { //如果type为1,则说明是AI的回答内容
                    msg = new AssistantMessage(m.getContent());
                }
                if (msg != null && StringUtils.hasText(msg.getText())) {
                    msgs.add(msg);
                }
            });
        }

        AiOperator aiOperator = this.getAiOperator(modelType);

        // 4.发送请求给大模型，获取问答结果
        // 将List转换为数组，确保类型一致
        org.springframework.ai.chat.messages.Message[] messagesArray =
                msgs.toArray(new org.springframework.ai.chat.messages.Message[0]);

        return aiOperator.chat_stream(messagesArray);
    }

    /**
     * 聊天
     *
     * @param queryVo
     * @return
     */
    public Flux<String> chatStream(QueryVo queryVo) {
        // 项目验证
        if (queryVo.getProjectId() == null) {
            return Flux.error(new IllegalArgumentException("项目ID不能为空"));
        }

        ChatProject project = chatProjectMapper.selectChatProjectByProjectId(queryVo.getProjectId());
        if (project == null) {
            return Flux.error(new RuntimeException("找不到ID为 " + queryVo.getProjectId() + " 的项目"));
        }

        // +++ 新增：在路由前保存用户消息 +++
        saveUserMessage(queryVo); // 确保用户提问消息存入MongoDB

        // 只对OpenAI项目启用智能体路由
        if (SystemConstant.MODEL_TYPE_OPENAI.equals(project.getType())) {
            log.info("【智能体路由】OpenAI项目进入路由系统");
            return agentCoordinatorService.coordinate(
                    queryVo.getMsg(),
                    queryVo.getChatId().toString(),
                    queryVo.getProjectId()
            );
        }

        // 非OpenAI项目直接处理
        log.info("【直接模型处理】非OpenAI项目直接处理");
        return this.directModelProcessing(queryVo);
    }

    /**
     * 保存用户消息到MongoDB（新增方法）
     * @param queryVo 查询参数
     */
    private void saveUserMessage(QueryVo queryVo) {
        String collectionName = MongoUtil.getMsgCollectionName(queryVo.getChatId());
        Message message = new Message();
        message.setId(IdUtil.getSnowflake().nextId());
        message.setChatId(queryVo.getChatId());
        message.setType(0); // 用户消息类型
        message.setContent(queryVo.getMsg());
        message.setCreateTime(new Date());

        // 检查敏感词
        for (String word : sensitiveWords) {
            if (message.getContent().contains(word)) {
                throw new RuntimeException("消息包含敏感词: " + word);
            }
        }

        mongoTemplate.insert(message, collectionName);
        log.info("保存用户消息到集合 {}: {}", collectionName, queryVo.getMsg());
    }

    /**
     * 保存聊天记录
     *
     * @param messageVo
     */
    public void saveMsg(MessageVo messageVo) {
        Message message = new Message();
        //生成聊天消息的id
        message.setId(IdUtil.getSnowflake().nextId());
        message.setChatId(messageVo.getChatId());
        message.setType(1);
        message.setContent(messageVo.getContent());
        message.setCreateTime(new Date());
        this.mongoTemplate.insert(message, MongoUtil.getMsgCollectionName(messageVo.getChatId()));
    }

    /**
     * 查询消息列表
     *
     * @param chatId
     * @return
     */
    public List<Message> listMsg(Long chatId) {
        return this.mongoTemplate.find(Query
                        .query(Criteria.where("chatId").is(chatId))
                        .with(Sort.by(Sort.Order.asc("createTime"))),
                Message.class, MongoUtil.getMsgCollectionName(chatId));
    }

    /**
     * 删除当前会话及其消息集合
     *
     * @param chatId
     * @param projectId
     */
    public void deleteChat(Long chatId, Long projectId) {
        //1.删除MongoDB中的聊天记录
        this.mongoTemplate.remove(Query
                        .query(Criteria.where("chatId").is(chatId)),
                Chat.class, MongoUtil.getChatCollectionName(projectId));

        //2.删除该聊天相关的所有消息
        this.mongoTemplate.remove(Query
                        .query(Criteria.where("chatId").is(chatId)),
                Message.class, MongoUtil.getMsgCollectionName(chatId));

        // 3. 删除当前会话的消息集合
        String msgCollectionName = MongoUtil.getMsgCollectionName(chatId);
        if (this.mongoTemplate.collectionExists(msgCollectionName)) {
            this.mongoTemplate.dropCollection(msgCollectionName);
        }
    }

    @Transactional
    public R upload(MultipartFile file, Long projectId) {
        try {
            // 最简单的成功返回方式
            R result = new R();
            result.setCode(200);
            result.setMsg("文件上传成功");
            return result;
        } catch (DataIntegrityViolationException e) {
            log.error("数据库存储失败: {}", e.getMessage());
            R result = new R();
            result.setCode(500);
            result.setMsg("文件内容过大，请压缩文件或拆分上传");
            return result;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            R result = new R();
            result.setCode(500);
            result.setMsg("上传失败: " + e.getMessage());
            return result;
        }
    }

    // 在 AiService 类中添加以下方法
    public void deleteLastTwoMessages(Long chatId) {
        String collectionName = MongoUtil.getMsgCollectionName(chatId);

        // 按创建时间倒序，取前两条消息
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, "createTime")).limit(2);
        List<Message> messages = mongoTemplate.find(query, Message.class, collectionName);

        // 删除这两条消息
        if (!messages.isEmpty()) {
            Criteria criteria = new Criteria().orOperator(
                    messages.stream().map(msg ->
                            Criteria.where("id").is(msg.getId())
                    ).toArray(Criteria[]::new)
            );
            Query deleteQuery = Query.query(criteria);
            mongoTemplate.remove(deleteQuery, Message.class, collectionName);
        }
    }

    @Override
    public void onApplicationEvent(RecordOptimizationAdvisor.DeleteMessagesEvent event) {
        deleteLastTwoMessages(event.getSessionId());
    }

    @Transactional
    public void deleteKnowledgeVectors(Long[] knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.length == 0) {
            return;
        }
        for (Long knowledgeId : knowledgeIds) {
            try {
                ChatKnowledge ck = this.chatKnowledgeMapper.selectChatKnowledgeByKnowledgeId(knowledgeId);
                if (ck == null) {
                    continue;
                }
                ChatProject project = this.chatProjectMapper.selectChatProjectByProjectId(ck.getProjectId());
                if (project == null) {
                    log.warn("删除向量跳过：未找到项目，knowledgeId={} projectId={}", knowledgeId, ck.getProjectId());
                    continue;
                }
                QdrantVectorStore targetStore = SystemConstant.MODEL_TYPE_OLLAMA.equals(project.getType())
                        ? ollamaVectorStore : openAiVectorStore;

                String pid = String.valueOf(ck.getProjectId());
                String kid = String.valueOf(ck.getKnowledgeId());

                // 优先按 projectId + knowledgeId 精确删除
                try {
                    targetStore.delete(new FilterExpressionBuilder()
                            .and(
                                    new FilterExpressionBuilder().eq("projectId", pid),
                                    new FilterExpressionBuilder().eq("knowledgeId", kid)
                            ).build());
                    log.info("已删除向量：projectId={}, knowledgeId={}", pid, kid);
                } catch (Exception preciseEx) {
                    // 兼容旧数据（可能缺少 knowledgeId 元数据）时，退化为按 projectId 删除
                    log.warn("按projectId+knowledgeId删除失败，尝试按projectId。pid={}, kid={}, err={}", pid, kid, preciseEx.getMessage());
                    targetStore.delete(new FilterExpressionBuilder().eq("projectId", pid).build());
                    log.info("已按projectId删除向量：projectId={}", pid);
                }
            } catch (Exception e) {
                log.error("删除向量失败 knowledgeId={}，原因：{}", knowledgeId, e.getMessage());
            }
        }
    }

    @Transactional
    public void deleteKnowledgeVectorsByProjectId(Long projectId) {
        try {
            ChatProject project = this.chatProjectMapper.selectChatProjectByProjectId(projectId);
            if (project == null) {
                log.warn("按projectId删除向量跳过：未找到项目，projectId={}", projectId);
                return;
            }
            QdrantVectorStore targetStore = SystemConstant.MODEL_TYPE_OLLAMA.equals(project.getType())
                    ? ollamaVectorStore : openAiVectorStore;
            String pid = String.valueOf(projectId);
            targetStore.delete(new FilterExpressionBuilder().eq("projectId", pid).build());
            log.info("已按projectId删除向量：projectId={}", pid);
        } catch (Exception e) {
            log.error("按projectId删除向量失败 projectId={}，原因：{}", projectId, e.getMessage());
        }
    }
}