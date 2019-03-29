package com.ldz.wechat.module.mapper;

import com.ldz.util.cache.MybatisRedisCache;
import com.ldz.wechat.module.model.SysZdxm;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.cache.decorators.FifoCache;
import tk.mybatis.mapper.common.Mapper;

@CacheNamespace(implementation=MybatisRedisCache.class, eviction=FifoCache.class)
public interface SysZdxmMapper extends Mapper<SysZdxm> {
}