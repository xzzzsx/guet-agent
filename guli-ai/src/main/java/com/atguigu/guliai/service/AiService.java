package com.atguigu.guliai.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
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
    private AgentCoordinatorService agentCoordinatorService; // 新增注入

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
     * 直接模型处理（绕过智能体路由系统）
     * 用于处理RouteAgent回退请求或其他非路由场景
     *
     * @param queryVo 查询参数
     * @return 模型响应流
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

        return this.processChatRequest(queryVo, project.getType());
    }

    /**
     * 核心聊天请求处理方法（无路由逻辑）
     */
    private Flux<String> processChatRequest(QueryVo queryVo, String modelType) {
        //1.记录用户的问题:MongoDB的对应的聊天的集合中
        String collectionName = MongoUtil.getMsgCollectionName(queryVo.getChatId());
        Message message = new Message();
        //聊天的id
        long msgId = IdUtil.getSnowflake().nextId();
        message.setId(msgId);
        message.setChatId(queryVo.getChatId());
        message.setType(0);
        message.setContent(queryVo.getMsg());
        // 检查敏感词
        for (String word : sensitiveWords) {
            if (message.getContent().contains(word)) {
                throw new RuntimeException("消息包含敏感词: " + word);
            }
        }
        message.setCreateTime(new Date());
        this.mongoTemplate.insert(message, collectionName);

        //2.查询本地知识库:Qdrant
        ChatProject chatProject = this.chatProjectMapper.selectChatProjectByProjectId(queryVo.getProjectId());
        if (chatProject == null) {
            throw new RuntimeException("对应的项目不存在!");
        }

        // 执行向量数据库检索
        List<Document> docs = new ArrayList<>();
        AiOperator retrievalOperator = this.getAiOperator(modelType);

        // 明确排除OpenAI模型的向量检索
        if (retrievalOperator instanceof OpenAiOperator) {
            log.info("OpenAI模型跳过向量数据库检索");
        } else if (retrievalOperator instanceof OllamaAiOperator) {
            docs = retrievalOperator.similaritySearch(queryVo);
            log.debug("执行向量检索，获取{}条文档", docs.size());
        } else {
            docs = new ArrayList<>();
            log.warn("未找到对应模型的检索实现，不执行向量数据库检索");
        }

        //3.查询历史问答,作为联系上下文的提示
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
        // 设置检索到的文档
        AiOperator aiOperator = this.getAiOperator(modelType);
        System.out.println("使用AI模型类型: " + modelType);
        // 仅对Ollama模型设置检索文档
        if (aiOperator instanceof OllamaAiOperator) {
            ((OllamaAiOperator) aiOperator).setRetrievedDocuments(docs);
        }
        // OpenAI模型不使用向量数据库检索结果

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

        // 添加项目ID空值检查
        if (queryVo.getProjectId() == null) {
            throw new IllegalArgumentException("项目ID不能为空");
        }

        // 只对OpenAI项目启用智能体路由
        ChatProject project = chatProjectMapper.selectChatProjectByProjectId(queryVo.getProjectId());
        if (project == null) {
            throw new RuntimeException("找不到ID为 " + queryVo.getProjectId() + " 的项目");
        }

        if (SystemConstant.MODEL_TYPE_OPENAI.equals(project.getType())) {
            log.info("【智能体路由】OpenAI项目进入路由系统");
            return agentCoordinatorService.coordinate(
                    queryVo.getMsg(),
                    queryVo.getChatId().toString(),
                    queryVo.getProjectId() // 直接使用请求中的 projectId
            );
        }

        // 非OpenAI项目直接处理
        log.info("【直接模型处理】非OpenAI项目直接处理");
        return this.directModelProcessing(queryVo);
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
}