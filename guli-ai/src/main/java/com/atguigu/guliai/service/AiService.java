package com.atguigu.guliai.service;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.atguigu.common.core.domain.model.LoginUser;
import com.atguigu.common.utils.SecurityUtils;
import com.atguigu.common.utils.StringUtils;
import com.atguigu.guliai.pojo.Chat;
import com.atguigu.guliai.pojo.Message;
import com.atguigu.guliai.strategy.AiBean;
import com.atguigu.guliai.strategy.AiOperator;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
public class AiService implements ApplicationContextAware {

    @Autowired
    private ChatKnowledgeMapper chatKnowledgeMapper;

    @Autowired
    private ChatProjectMapper chatProjectMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private List<String> sensitiveWords;

    private static final Map<String, AiOperator> MAP = new ConcurrentHashMap<>();

    /**
     * 统一获取spring容器中的AiOperator具体策略类,并放入map中方便切换
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
            MAP.put(aiBean.value(), (AiOperator)bean);
        });
    }

    public AiOperator getAiOperator(String type) {
        return MAP.get(type);
    }

    /**
     * 获取知识库列表
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
     * @param chatVo
     */
    public void updateChat(ChatVo chatVo) {
        //根据id更新会话标题
        this.mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(chatVo.getChatId())),
                Update.update("title", chatVo.getTitle()), MongoUtil.getChatCollectionName(chatVo.getProjectId()));
    }

    /**
     * 聊天
     * @param queryVo
     * @return
     */
    public Flux<String> chatStream(QueryVo queryVo) {
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
        //获取项目所采用的模型类型
        String type = chatProject.getType();
        // 执行向量数据库检索并记录日志
        AiOperator retrievalOperator = this.getAiOperator(type);
        List<Document> docs = retrievalOperator.similaritySearch(queryVo);
 //本地知识库的内容,要作为系统提示

        //3.查询历史问答,作为联系上下文的提示
        List<Message> messages = this.mongoTemplate.find(Query
                .query(Criteria.where("chatId").is(queryVo.getChatId()))
                .with(Sort.by(Sort.Order.asc("createTime"))), Message.class, collectionName);

        //组装上下文提示
        List<org.springframework.ai.chat.messages.Message> msgs = new ArrayList<>();
        if (!CollectionUtils.isEmpty(messages)) {//用户问答提示
            messages.forEach(m -> {
                org.springframework.ai.chat.messages.Message msg = null;
                if (m.getType().intValue() == 0) { //如果type为0,则说明是用户的提问UserMessage
                    msg = new UserMessage(m.getContent());
                } else { //如果type为1,则说明是AI的回答内容
                    msg = new AssistantMessage(m.getContent());
                }
                if (msg != null && StringUtils.hasText(msg.getText())) { msgs.add(msg); }
            });
        }
        // 设置检索到的文档
        AiOperator aiOperator = this.getAiOperator(type);
        System.out.println("使用AI模型类型: " + type);
        /*if (aiOperator instanceof OllamaAiOperator) {
            ((OllamaAiOperator) aiOperator).setRetrievedDocuments(docs);
        }*/
        // 为OpenAI模型设置检索到的文档
        if (aiOperator instanceof OpenAiOperator) {
            ((OpenAiOperator) aiOperator).setRetrievedDocuments(docs);
        }

        // 4.发送请求给大模型，获取问答结果
        // 将List转换为数组，确保类型一致
        org.springframework.ai.chat.messages.Message[] messagesArray =
                msgs.toArray(new org.springframework.ai.chat.messages.Message[0]);

        Flux<String> result = aiOperator.chat_stream(messagesArray);

        //5.ai模型的响应结果,如果直接从流中提取结果的话和页面从流中提取的结果 完全不一样
        return result;
    }

    /**
     * 保存聊天记录
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
        if(this.mongoTemplate.collectionExists(msgCollectionName)) {
            this.mongoTemplate.dropCollection(msgCollectionName);
        }
    }


}
