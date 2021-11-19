package io.clownfish.clownfish.daointerface;

import io.clownfish.clownfish.dbentities.CfJavaversion;

import java.util.List;

public interface CfJavaversionDAO
{
    List<CfJavaversion> findByJavaref(long ref);
    long findMaxVersion(long ref);
    CfJavaversion findByPK(long ref, long version);
    List<CfJavaversion> findAll();
    CfJavaversion create(CfJavaversion entity);
    boolean delete(CfJavaversion entity);
    CfJavaversion edit(CfJavaversion entity);
}
