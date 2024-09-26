package com.ethlo.http.logger.clickhouse;

import org.springframework.data.relational.core.dialect.AnsiDialect;
import org.springframework.data.relational.core.dialect.LimitClause;
import org.springframework.data.relational.core.dialect.LockClause;

public class ClickhouseDialect extends AnsiDialect
{
    @Override
    public LockClause lock()
    {
        throw new UnsupportedOperationException("Locking is unsupported in Clickhouse");
    }

    @Override
    public LimitClause limit()
    {
        return new LimitClause()
        {
            @Override
            public String getLimit(long limit)
            {
                return String.format("LIMIT %d", limit);
            }

            @Override
            public String getOffset(long offset)
            {
                return getLimitOffset(Long.MAX_VALUE, offset);
            }

            @Override
            public String getLimitOffset(long limit, long offset)
            {
                return String.format("LIMIT %d OFFSET %d", limit, offset);
            }

            @Override
            public Position getClausePosition()
            {
                return Position.AFTER_ORDER_BY;
            }
        };
    }
}