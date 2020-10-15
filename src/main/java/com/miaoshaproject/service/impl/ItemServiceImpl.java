package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.ItemDoMapper;
import com.miaoshaproject.dao.ItemStockDoMapper;
import com.miaoshaproject.dataobject.ItemDo;
import com.miaoshaproject.dataobject.ItemStockDo;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EmBusinessError;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.PromoModel;
import com.miaoshaproject.validator.ValidationResult;
import com.miaoshaproject.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {

    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemDoMapper itemDoMapper;

    @Autowired
    private ItemStockDoMapper itemStockDoMapper;

    @Autowired
    private PromoService promoService;

    private ItemDo convertItemFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }

        ItemDo itemDo = new ItemDo();
        BeanUtils.copyProperties(itemModel,itemDo);

        return itemDo;
    }

    private ItemStockDo convertItemStockDoFromItemModel(ItemModel itemModel){
        if(itemModel == null){
            return null;
        }

        ItemStockDo itemStockDo = new ItemStockDo();
        itemStockDo.setItemId(itemModel.getId());
        itemStockDo.setStock(itemModel.getStock());

        return itemStockDo;
    }

    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        //校验入参
        ValidationResult result = validator.validate(itemModel);
        if(result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }

        //转化itemModel->dataobject
        ItemDo itemDo = this.convertItemFromItemModel(itemModel);

        //写入数据库
        itemDoMapper.insertSelective(itemDo);
        itemModel.setId(itemDo.getId());

        ItemStockDo itemStockDo = this.convertItemStockDoFromItemModel(itemModel);
        itemStockDoMapper.insertSelective(itemStockDo);

        //返回创建完成的对象
        return this.getItemById(itemModel.getId());
    }

    @Override
    public List<ItemModel> listItem() {
        List<ItemDo> itemDoList = itemDoMapper.listItem();
        List<ItemModel> itemModelList = itemDoList.stream().map(itemDo -> {
            ItemStockDo itemStockDo = itemStockDoMapper.selectByItemId(itemDo.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDo, itemStockDo);
            return itemModel;
        }).collect(Collectors.toList());

        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) throws BusinessException {
        ItemDo itemDo = itemDoMapper.selectByPrimaryKey(id);
        if(itemDo == null){
            return null;
        }

        //操作获取库存数量
        ItemStockDo itemStockDo = itemStockDoMapper.selectByItemId(itemDo.getId());
        if(itemStockDo == null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"获取库存数量失败");
        }

        //将dataobject->model
        ItemModel itemModel = this.convertModelFromDataObject(itemDo, itemStockDo);

        //获取活动商品信息
        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        if(promoModel != null && promoModel.getStatus().intValue() != 3){
            itemModel.setPromoModel(promoModel);
        }

        return itemModel;
    }

    private ItemModel convertModelFromDataObject(ItemDo itemDO, ItemStockDo itemStockDO) {
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO, itemModel);
        itemModel.setStock(itemStockDO.getStock());
        return itemModel;
    }

    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
        int affectedRow = itemStockDoMapper.decreaseStock(itemId, amount);
        if(affectedRow>0){
            //更新成功
            return true;
        }else{
            //更新失败
            return false;
        }
    }

    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) throws BusinessException {
        itemDoMapper.increaseSales(itemId,amount);
    }
}
