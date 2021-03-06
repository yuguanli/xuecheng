package com.xuecheng.manage_course.service;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xuecheng.framework.domain.cms.CmsPage;
import com.xuecheng.framework.domain.cms.response.CmsPageResult;
import com.xuecheng.framework.domain.cms.response.CmsPostPageResult;
import com.xuecheng.framework.domain.course.*;
import com.xuecheng.framework.domain.course.ext.*;
import com.xuecheng.framework.domain.course.request.CourseListRequest;
import com.xuecheng.framework.domain.course.response.AddCourseResult;
import com.xuecheng.framework.domain.course.response.CourseCode;
import com.xuecheng.framework.exception.ExceptionCast;
import com.xuecheng.framework.model.response.CommonCode;
import com.xuecheng.framework.model.response.QueryResponseResult;
import com.xuecheng.framework.model.response.QueryResult;
import com.xuecheng.framework.model.response.ResponseResult;
import com.xuecheng.manage_course.client.CmsPageClient;
import com.xuecheng.manage_course.config.CoursePublicsh;
import com.xuecheng.manage_course.dao.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class CourseService {

    @Autowired
    TeachplanMapper teachplanMapper;

    @Autowired
    TeachplanRepository teachplanRepository;

    @Autowired
    CourseBaseRepository courseBaseRepository;

    @Autowired
    CourseMapper courseMapper;

    @Autowired
    CategoryMapper categoryMapper;
    @Autowired
    CourseMarketRepository courseMarketRepository;
    @Autowired
    CoursePicRepository coursePicRepository;

    @Autowired
    CoursePublicsh coursePublicsh;

    @Autowired
    CmsPageClient cmsPageClient;
    @Autowired
    CoursePubRepository coursePubRepository;

    //查询课程计划
    public TeachplanNode findTeachplanList(String courseId){
        return teachplanMapper.selectList(courseId);
    }
    //添加课程计划
    @Transactional
    public ResponseResult addTeachplan(Teachplan teachplan){
        //校验课程id和课程计划名称
        if(teachplan == null ||
                StringUtils.isEmpty(teachplan.getCourseid()) ||
                StringUtils.isEmpty(teachplan.getPname())){
            ExceptionCast.Cast(CommonCode.INVALID_PARAM);
        }
        //取出课程id
        String courseid = teachplan.getCourseid();
        //取出父结点id
        String parentid = teachplan.getParentid();
        if (StringUtils.isEmpty(parentid)){
            parentid=getTeachplanRoot(courseid);
        }
        //取出父结点信息
        Optional<Teachplan> teachplanOptional = teachplanRepository.findById(parentid);
        if(!teachplanOptional.isPresent()){
            ExceptionCast.Cast(CommonCode.INVALID_PARAM);
        }
        //父结点
        Teachplan teachplanParent = teachplanOptional.get();
        //父结点级别
        String parentGrade = teachplanParent.getGrade();
        //设置父结点
        teachplan.setParentid(parentid);
        teachplan.setStatus("0");//未发布
        //子结点的级别，根据父结点来判断
        teachplan.setGrade(parentGrade.equals("1")?"2":"3");
        //设置课程id
        teachplan.setCourseid(teachplanParent.getCourseid());
        teachplanRepository.save(teachplan);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    private String getTeachplanRoot(String courseid) {
        //查询顶级节点信息
        List<Teachplan> teachplans = teachplanRepository.findByCourseidAndParentid(courseid, "0");
        if (teachplans==null||teachplans.size()<=0){
            //查询课程基本信息
            Optional<CourseBase> id = courseBaseRepository.findById(courseid);
            if (!id.isPresent()){
                ExceptionCast.Cast(CommonCode.INVALID_PARAM);
            }
            //获取课程基本信息
            CourseBase courseBase = id.get();
            //新增一个根结点
            Teachplan teachplanRoot = new Teachplan();
            teachplanRoot.setCourseid(courseid);
            teachplanRoot.setPname(courseBase.getName());
            teachplanRoot.setParentid("0");
            teachplanRoot.setGrade("1");//1级
            teachplanRoot.setStatus("0");//未发布
            teachplanRepository.save(teachplanRoot);
            return teachplanRoot.getId();
        }
        Teachplan teachplan = teachplans.get(0);
        return teachplan.getId();
    }

    //查询我的课程
    public QueryResponseResult findList(int page, int size, CourseListRequest courseListRequest){
        if(courseListRequest == null){
            //校验查询条件
            courseListRequest = new CourseListRequest();
        }
        if(page<=0){
            page = 0;
        }
        if(size<=0){
            size = 20;
        }
        //设置分页参数
        PageHelper.startPage(page,size);
        Page<CourseInfo> courseListPage = courseMapper.findCourseListPage(courseListRequest);
        QueryResult resou=new QueryResult();
        resou.setList(courseListPage.getResult());
        resou.setTotal(courseListPage.getTotal());
        return new QueryResponseResult(CommonCode.SUCCESS,resou);
    }

    //查询分类信息
    public CategoryNode findCategoryList(){
        CategoryNode categoryNode = categoryMapper.selectList();
        return categoryNode;
    }
    //添加课程基本信息
    public AddCourseResult addCourse(CourseBase courseBase){
        //校验必填项
        if (StringUtils.isEmpty(courseBase.getName())||StringUtils.isEmpty(courseBase.getStudymodel())){
            ExceptionCast.Cast(CommonCode.INVALID_PARAM);
        }
        //课程状态默认为未发布
        courseBase.setStatus("202001");
        courseBaseRepository.save(courseBase);
        return new AddCourseResult(CommonCode.SUCCESS,courseBase.getId());
    }

    //根据课程ID查询课程基本信息
    public CourseBase findByid(String courseId) {
        Optional<CourseBase> id = courseBaseRepository.findById(courseId);
        if (id.isPresent()){
            return id.get();
        }
        return null;
    }

    //更新课程基本信息
    @Transactional
    public ResponseResult saveBase( String id, CourseBase courseBase){
        CourseBase one = this.findByid(id);
        if(one == null){
            //抛出异常
            ExceptionCast.Cast(CommonCode.INVALID_PARAM);
        }
        //修改课程信息
        one.setName(courseBase.getName());
        one.setMt(courseBase.getMt());
        one.setSt(courseBase.getSt());
        one.setGrade(courseBase.getGrade());
        one.setStudymodel(courseBase.getStudymodel());
        one.setUsers(courseBase.getUsers());
        one.setDescription(courseBase.getDescription());
        CourseBase save = courseBaseRepository.save(one);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    //根据ID查询课程营销信息
    public CourseMarket getCourseMarketById(String courseId) {
        Optional<CourseMarket> id = courseMarketRepository.findById(courseId);
        if (!id.isPresent()){
            return null;
        }
        return id.get();
    }

    //更新营销计划,如果没有就创建营销计划
    @Transactional
    public ResponseResult updateCourseMarket(String id, CourseMarket courseMarket) {
        CourseMarket one = this.getCourseMarketById(id);
        if (one==null){
            //添加课程营销信息
            one = new CourseMarket();
            BeanUtils.copyProperties(courseMarket, one);
            //设置课程id
            one.setId(id);
            courseMarketRepository.save(one);
        }else {
            one.setCharge(courseMarket.getCharge());
            one.setStartTime(courseMarket.getStartTime());//课程有效期，开始时间
            one.setEndTime(courseMarket.getEndTime());//课程有效期，结束时间
            one.setPrice(courseMarket.getPrice());
            one.setQq(courseMarket.getQq());
            one.setValid(courseMarket.getValid());
            courseMarketRepository.save(one);
        }
        return new ResponseResult(CommonCode.SUCCESS);
    }


    //添加课程图片
    @Transactional
    public ResponseResult saveCoursePic(String courseId,String pic){
        //查询课程图片
        Optional<CoursePic> picOptional = coursePicRepository.findById(courseId);
        CoursePic coursePic = null;
        if(picOptional.isPresent()){
            coursePic = picOptional.get();
        }
        //没有课程图片则新建对象
        if(coursePic == null){
            coursePic = new CoursePic();
        }
        coursePic.setCourseid(courseId);
        coursePic.setPic(pic);
        //保存课程图片
        coursePicRepository.save(coursePic);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    //查询根据ID课程图片
    public CoursePic findCoursepic(String courseId) {
        Optional<CoursePic> byId = coursePicRepository.findById(courseId);
        if (!byId.isPresent()){
            return null;
        }
        return byId.get();
    }


    //删除课程图片
    @Transactional
    public ResponseResult deleteCoursePic(String courseId) {
        //执行删除
        coursePicRepository.deleteById(courseId);
        return new ResponseResult(CommonCode.SUCCESS);
    }

    //查询课程视图
    public CourseView getCoruseView(String id) {
        CourseView courseView = new CourseView();
        //查询课程基本信息
        Optional<CourseBase> optional = courseBaseRepository.findById(id);
        if(optional.isPresent()){
            CourseBase courseBase = optional.get();
            courseView.setCourseBase(courseBase);
        }
        //查询课程营销信息
        Optional<CourseMarket> courseMarketOptional = courseMarketRepository.findById(id);
        if(courseMarketOptional.isPresent()){
            CourseMarket courseMarket = courseMarketOptional.get();
            courseView.setCourseMarket(courseMarket);
        }
        //查询课程图片信息
        Optional<CoursePic> picOptional = coursePicRepository.findById(id);
        if(picOptional.isPresent()){
            CoursePic coursePic = picOptional.get();
            courseView.setCoursePic(picOptional.get());
        }
        //查询课程计划信息
        TeachplanNode teachplanNode = teachplanMapper.selectList(id);
        courseView.setTeachplanNode(teachplanNode);
        return courseView;
    }

    //课程预览
    public CoursePublishResult preview(String id) {
        CourseBase one = this.findByid(id);
        //发布课程预览页面
        CmsPage cmsPage = new CmsPage();
        //站点
        cmsPage.setSiteId(coursePublicsh.getSiteId());//课程预览站点
        //模板
        cmsPage.setTemplateId(coursePublicsh.getTemplateId());
        //页面名称
        cmsPage.setPageName(id+".html");
        //页面别名
        cmsPage.setPageAliase(one.getName());
        //页面访问路径
        cmsPage.setPageWebPath(coursePublicsh.getPageWebPath());
        //页面存储路径
        cmsPage.setPagePhysicalPath(coursePublicsh.getPagePhysicalPath());
        //数据url
        cmsPage.setDataUrl(coursePublicsh.getDataUrlPre()+id);
        //远程请求cms保存页面信息
        CmsPageResult cmsPageResult = cmsPageClient.save(cmsPage);
        if(!cmsPageResult.isSuccess()){
            return new CoursePublishResult(CommonCode.FAIL,null);
        }
        //页面id
        String pageId = cmsPageResult.getCmsPage().getPageId();
        //页面url
        String pageUrl = coursePublicsh.getPreviewUrl()+pageId;
        return new CoursePublishResult(CommonCode.SUCCESS,pageUrl);

    }

    //一键发布课程页面
    @Transactional
    public CoursePublishResult publish(String id) {
        CourseBase one = this.findByid(id);
        //发布课程预览页面
        CmsPage cmsPage = new CmsPage();
        //站点
        cmsPage.setSiteId(coursePublicsh.getSiteId());//课程预览站点
        //模板
        cmsPage.setTemplateId(coursePublicsh.getTemplateId());
        //页面名称
        cmsPage.setPageName(id + ".html");
        //页面别名
        cmsPage.setPageAliase(one.getName());
        //页面访问路径
        cmsPage.setPageWebPath(coursePublicsh.getPageWebPath());
        //页面存储路径
        cmsPage.setPagePhysicalPath(coursePublicsh.getPagePhysicalPath());
        //数据url
        cmsPage.setDataUrl(coursePublicsh.getDataUrlPre() + id);
        //远程调用一键发布课程页面返回url
        CmsPostPageResult cmsPostPageResult = cmsPageClient.postPageQuick(cmsPage);
        if (!cmsPostPageResult.isSuccess()) {
            return new CoursePublishResult(CommonCode.FAIL, null);
        }
        //更新课程状态
        CourseBase courseBase = saveCoursePubState(id);
        //添加课程索引到数据库,用Logstash同步到索引库
        CoursePub coursePub = createCoursePub(id);
        //保存 coursePub,有则更改,无则添加
        CoursePub coursePub1 = saveCoursePub(id, coursePub);
        //课程缓存...
        //页面url
        String pageUrl = cmsPostPageResult.getPageUrl();
        return new CoursePublishResult(CommonCode.SUCCESS, pageUrl);
    }

    //保存 coursePub,有则更改,无则添加
    private CoursePub saveCoursePub(String id, CoursePub coursePub) {
        CoursePub coursePubNew = null;
        Optional<CoursePub> coursePubOptional = coursePubRepository.findById(id);
        if(coursePubOptional.isPresent()){
            coursePubNew = coursePubOptional.get();
        }else {
            coursePubNew = new CoursePub();
        }
        BeanUtils.copyProperties(coursePub,coursePubNew);
        //更新时间戳为最新时间
        coursePubNew.setTimestamp(new Date());
        coursePubNew.setPubTime(new SimpleDateFormat("YYYY-MM-dd HH:mm:ss").format(new Date()));
        coursePubNew.setId(id);
        coursePubRepository.save(coursePub);
        return coursePub;
    }

    //创建一个coursePub对象 拼装信息
    private CoursePub createCoursePub(String id) {
        CoursePub coursePub = new CoursePub();
        //封装课程基本信息
        CourseBase base = this.findByid(id);
        if (base!=null){
            BeanUtils.copyProperties(base,coursePub);
        }
        //封装课程图片信息
        CoursePic coursepic = this.findCoursepic(id);
        if (coursepic!=null){
            BeanUtils.copyProperties(coursepic,coursePub);
        }
        //课程营销信息
        Optional<CourseMarket> marketOptional = courseMarketRepository.findById(id);
        if(marketOptional.isPresent()){
            CourseMarket courseMarket = marketOptional.get();
            BeanUtils.copyProperties(courseMarket, coursePub);
        }
        //课程计划信息转成Json字符串存入
        TeachplanNode teachplanNode = teachplanMapper.selectList(id);
        String string = JSON.toJSONString(teachplanNode);
        coursePub.setTeachplan(string);
        return coursePub;
    }

    private CourseBase saveCoursePubState(String id) {
        CourseBase base = findByid(id);
        if (base==null){
            ExceptionCast.Cast(CommonCode.INVALID_PARAM);
        }
        base.setStatus("202002");
        courseBaseRepository.save(base);
        return base;
    }

}
