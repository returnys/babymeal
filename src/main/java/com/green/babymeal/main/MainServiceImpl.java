package com.green.babymeal.main;

import com.green.babymeal.common.config.security.AuthenticationFacade;
import com.green.babymeal.common.entity.*;
import com.green.babymeal.common.repository.ProductCategoryRelationRepository;
import com.green.babymeal.main.model.MainSelPaging;
import com.green.babymeal.main.model.MainSelVo;
import com.green.babymeal.main.model.SelDto;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.QBean;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class MainServiceImpl implements MainService {

    private final EntityManager em;
    private final JPAQueryFactory jpaQueryFactory;
    private final AuthenticationFacade USERPK;
    private final ProductCategoryRelationRepository productCategoryRelationRepository;


    @Override
    public MainSelPaging mainSel(SelDto dto) {

        QProductEntity qProductEntity = new QProductEntity("ProductEntity");
        QProductThumbnailEntity qProductThumbnailEntity = new QProductThumbnailEntity("ProductThumbnailEntity");
        QProductCateRelationEntity qProductCateRelationEntity = new QProductCateRelationEntity("ProductCateRelationEntity");

        int startIdx = (dto.getPage() - 1) * dto.getRow();

        if (dto.getCheck() == 1) {


            List<MainSelVo> fetch = jpaQueryFactory
                    .select(getBean(qProductEntity, qProductThumbnailEntity))
                    .from(qProductEntity)
                    .leftJoin(qProductEntity.productThumbnailEntityList, qProductThumbnailEntity)
                    .on(qProductEntity.productId.eq(qProductThumbnailEntity.productId.productId))
                    .where(qProductEntity.pQuantity.ne(0), qProductEntity.isDelete.eq((byte) 0), qProductThumbnailEntity.img.isNotNull())
                    .orderBy(qProductEntity.createdAt.desc())
                    .offset(startIdx)
                    .limit(dto.getRow())
                    .fetch();

            long count = jpaQueryFactory
                    .select(qProductEntity, qProductThumbnailEntity)
                    .from(qProductEntity)
                    .leftJoin(qProductEntity.productThumbnailEntityList, qProductThumbnailEntity)
                    .on(qProductEntity.productId.eq(qProductThumbnailEntity.productId.productId))
                    .orderBy(qProductEntity.createdAt.desc())
                    .fetchCount();


            productNmCateId(fetch); //상품 이름에 단계를 붙힌다


            return MainSelPaging.builder()
                    .maxPage((int) Math.ceil((double) count / dto.getRow()))
                    .maxCount(count)
                    .list(fetch)
                    .build();

        } else if (dto.getCheck() == 2) {
            Object userBabyBirth = em.createQuery("select u.birthday from UserEntity u where u.id=:iuser ")
                    .setParameter("iuser", USERPK.getLoginUser().getIuser()).getSingleResult();
            LocalDate userBabyBirthday = (LocalDate) userBabyBirth;
            Period between = Period.between(userBabyBirthday, LocalDate.now());
            System.out.println("between.getMonths() = " + between.getMonths());
            Long cate;
            if (between.getMonths() <= 4) {
                return null;
            } else if (between.getMonths() <= 6) {
                cate = 1L;
            } else if (between.getMonths() <= 10) {
                cate = 2L;
            } else if (between.getMonths() <= 13) {
                cate = 3L;
            } else cate = 4L;

            System.out.println("cate = " + cate);

            List<MainSelVo> fetch = jpaQueryFactory
                    .select(getBean(qProductEntity, qProductThumbnailEntity))
                    .from(qProductEntity)
                    .leftJoin(qProductThumbnailEntity)
                    .on(qProductEntity.productId.eq(qProductThumbnailEntity.productId.productId))
                    .leftJoin(qProductCateRelationEntity)
                    .on(qProductEntity.productId.eq(qProductCateRelationEntity.productEntity.productId))
                    .where(qProductCateRelationEntity.categoryEntity.cateId.eq(cate), qProductEntity.pQuantity.ne(0),
                            qProductEntity.isDelete.eq((byte) 0), qProductThumbnailEntity.img.isNotNull())
                    .orderBy(qProductEntity.saleVolume.desc(), Expressions.numberTemplate(Double.class, "function('rand')").asc())
                    .limit(dto.getRow())
                    .fetch();

            System.out.println("fetch = " + fetch);

            for (MainSelVo vo : fetch) {
                vo.setName("[" + cate + "단계]" + vo.getName());
            }

            return MainSelPaging.builder()
                    .maxCount(Long.valueOf(fetch.size()))
                    .list(fetch)
                    .build();


        }

        if (dto.getCheck() == 3) {
            List<MainSelVo> fetch = jpaQueryFactory.select(getBean(qProductEntity, qProductThumbnailEntity))
                    .from(qProductEntity)
                    .leftJoin(qProductThumbnailEntity)
                    .on(qProductEntity.productId.eq(qProductThumbnailEntity.productId.productId))
                    .where(qProductEntity.pQuantity.ne(0), qProductEntity.isDelete.eq((byte) 0), qProductThumbnailEntity.img.isNotNull())
                    .orderBy(Expressions.numberTemplate(Double.class, "function('rand')").desc())
                    .limit(dto.getRow())
                    .fetch();

            productNmCateId(fetch); //상품 이름에 단계를 붙힌다

            return MainSelPaging.builder()
                    .maxCount(Long.valueOf(fetch.size()))
                    .list(fetch)
                    .build();


        }
        return null;
    }

    private void productNmCateId(List<MainSelVo> fetch) {
        for (MainSelVo vo : fetch) {
            ProductEntity productEntity = new ProductEntity();
            productEntity.setProductId(vo.getProductId());
            ProductCateRelationEntity byProductEntity = productCategoryRelationRepository.findByProductEntity(productEntity);
            Long productCateId = byProductEntity.getProductCateId();
            vo.setName("[" + productCateId + "단계]" + vo.getName());
        }
    }


    private static QBean<MainSelVo> getBean(QProductEntity qProductEntity, QProductThumbnailEntity qProductThumbnailEntity) {
        return Projections.bean(MainSelVo.class,
                qProductEntity.productId,
                qProductThumbnailEntity.img.as("thumbnail"),
                qProductEntity.pName.as("name"),
                qProductEntity.pPrice.as("price"),
                qProductEntity.pQuantity.as("quantity"),
                qProductEntity.saleVolume.as("saleVoumn"),
                qProductEntity.pointRate);
    }

}
//
//          Long aLong = jpaQueryFactory
//                  .select(qProductEntity.count())
//                  .from(qProductEntity)
//                  .fetchOne();
//          int maxPage = ;


//      }
//      return null;

///       if(dto.getCheck()==2){
///
///       }
//       return null;
//}


//    public MainSelPaging mainSel(SelDto dto){
//        if(dto.getCheck()==1){
////            int selMaxPageCount = mapper.selMainCount();
////            int maxPage=(int)Math.ceil((double)selMaxPageCount/dto.getRow());
////            int startIdx=(dto.getPage()-1)*dto.getRow();
////            List<MainSelVo> mainSelVos = mapper.selMainVo(startIdx,dto.getRow());
////            thumbnailNm(mainSelVos);
////
////            MainSelPaging mainSelPaging=MainSelPaging.builder()
////                            .maxPage(maxPage)
////                            .maxCount(selMaxPageCount)
////                            .list(mainSelVos)
////                            .build();
////
////            return mainSelPaging;
//            QProductEntity qProductEntity=new QProductEntity("productEntity");
//            List<ProductEntity> fetch = jpaQueryFactory
//                    .selectFrom(qProductEntity).fetch();
//           return MainSelPaging.builder()
//                    .entities(fetch)
//                    .build();
//
//
//        }
//
//        else if(dto.getCheck()==2){
//
//            int month = mapper.birth(USERPK.getLoginUser().getIuser());
//            log.info("{}:",month);
//            int cate = 0;
//            if (month <= 4) {
//                return null;
//            }
//            if (month > 4 && month <= 6) {
//                cate = 1;
//            } else if (month > 6 && month <= 10) {
//                cate = 2;
//            } else if (month > 10 && month <= 13) {
//                cate = 3;
//            } else if (month > 13) {
//                cate = 4;
//            }
//            log.info("{}:",USERPK.getLoginUser().getIuser());
//            List<MainSelVo> mainSelVos = mapper.birthRecommendFilter(cate, dto.getRow());
//            thumbnailNm(mainSelVos);
//            log.info("{}:",mainSelVos);
//            MainSelPaging mainSelPaging=MainSelPaging.builder()
//                    .list(mainSelVos)
//                    .build();
//
//            return mainSelPaging;
//
//        }
//
//
//        else if(dto.getCheck()==3){
//            List<MainSelVo> mainSelVos = mapper.random();
//            thumbnailNm(mainSelVos);
//            MainSelPaging mainSelPaging= MainSelPaging.builder()
//                    .list(mainSelVos)
//                    .build();
//            return mainSelPaging;
//
//        }
//
//
//       else if(dto.getCheck()==4){
//            List<MainSelVo> mainSelVos = mapper.bestSel();
//            thumbnailNm(mainSelVos);
//            MainSelPaging mainSelPaging=MainSelPaging.builder()
//                    .list(mainSelVos)
//                    .build();
//            return mainSelPaging;
//        }
//
//
//       else {
//            int startIdx=(dto.getPage()-1)*dto.getRow();
//            int maxPageCount = mapper.bestSelAllCount();
//            int maxPage=(int)Math.ceil((double)maxPageCount/dto.getRow());
//            List<MainSelVo> mainSelVos = mapper.bestSelAll(startIdx,dto.getRow());
//            thumbnailNm(mainSelVos);
//            MainSelPaging mainSelPaging=MainSelPaging.builder()
//                    .maxPage(maxPage)
//                    .maxCount(maxPageCount)
//                    .list(mainSelVos)
//                    .build();
//            return mainSelPaging;
//
//        }
//    }
//
//
//
//
//    private void thumbnailNm(List<MainSelVo> mainSelVos) {
//        for (int i = 0; i < mainSelVos.size(); i++) {
//            String thumbnail = mainSelVos.get(i).getThumbnail();
//            Long productId = mainSelVos.get(i).getProductId();
//            String fullPath ="http://192.168.0.144:5001/img/product/"+productId+"/"+thumbnail;
//            mainSelVos.get(i).setThumbnail(fullPath);
//            Long levelSel = mapper.levelSel(mainSelVos.get(i).getProductId());
//            log.info("{}:", mainSelVos.get(i).getProductId());
//            if(levelSel==null){
//                continue;
//            }
//            String name = mainSelVos.get(i).getName();
//            String levelName="["+levelSel+"단계] "+name;
//            mainSelVos.get(i).setName(levelName);
//        }
//    }


