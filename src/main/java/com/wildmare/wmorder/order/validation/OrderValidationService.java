package com.wildmare.wmorder.order.validation;

import com.wildmare.wmorder.config.ConfigManager;
import com.wildmare.wmorder.item.ItemRestrictionService;
import com.wildmare.wmorder.order.model.OrderDraft;
import com.wildmare.wmorder.permission.LimitProfile;
import com.wildmare.wmorder.util.MoneyMath;
import com.wildmare.wmorder.util.OperationResult;

import java.math.BigDecimal;
import java.time.Duration;

public final class OrderValidationService {
    private final ConfigManager configs;private final ItemRestrictionService restrictions;private final MoneyMath money;
    public OrderValidationService(ConfigManager configs,ItemRestrictionService restrictions,MoneyMath money){this.configs=configs;this.restrictions=restrictions;this.money=money;}
    public OperationResult<BigDecimal> validate(OrderDraft draft,LimitProfile profile){
        OperationResult<Void> item=restrictions.validate(draft.item());if(!item.success())return OperationResult.failure("invalid_item",item.detail());
        long global=configs.settings().orders().maximumItemsPerTransaction()*1000L;
        long maximum=Math.min(profile.maxQuantityPerOrder(),global);
        if(draft.quantity()<=0||draft.quantity()>maximum)return OperationResult.failure("invalid_quantity","Maximum is "+maximum);
        BigDecimal price;
        try{price=money.normalize(draft.pricePerItem());}catch(ArithmeticException|IllegalArgumentException e){return OperationResult.failure("invalid_price",e.getMessage());}
        if(price.signum()<=0||price.compareTo(configs.settings().economy().minimumPricePerItem())<0||price.compareTo(configs.settings().economy().maximumPricePerItem())>0)
            return OperationResult.failure("invalid_price","Price outside configured bounds");
        BigDecimal total;
        try{total=money.multiply(price,draft.quantity());}catch(ArithmeticException e){return OperationResult.failure("money_overflow",e.getMessage());}
        BigDecimal max=configs.settings().economy().maximumTotalOrderValue().min(profile.maxTotalValue());
        if(total.compareTo(configs.settings().economy().minimumTotalOrderValue())<0||total.compareTo(max)>0)return OperationResult.failure("invalid_total","Total outside configured bounds");
        Duration duration=draft.duration();
        if(duration.isNegative()||duration.isZero()||duration.compareTo(configs.settings().orders().minimumDuration())<0||duration.compareTo(configs.settings().orders().maximumDuration())>0||duration.compareTo(profile.duration())>0)
            return OperationResult.failure("invalid_duration","Duration is outside your limit");
        return OperationResult.success(total);
    }
}
