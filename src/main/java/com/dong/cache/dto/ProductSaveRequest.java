package com.dong.cache.dto;

import jakarta.validation.constraints.Digits;
import com.dong.cache.entity.Product;
import com.dong.cache.enums.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 商品保存请求。
 */
public class ProductSaveRequest {

    /**
     * 名称。
     */
    @NotBlank
    private String name;

    /**
     * 商品分类。
     */
    private String category;

    /**
     * 商品价格。
     */
    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal price;

    /**
     * 库存数量。
     */
    @NotNull
    private Integer stock;

    /**
     * 门店经度。可选，不填表示这个商品不参与地理检索，
     * 填了就必须和纬度一起给，只有经度是算不出距离的。
     */
    @DecimalMin("-180")
    @DecimalMax("180")
    @Digits(integer = 3, fraction = 6)
    private Double longitude;

    /**
     * 门店纬度。可选，不填表示这个商品不参与地理检索。
     */
    @DecimalMin("-90")
    @DecimalMax("90")
    @Digits(integer = 2, fraction = 6)
    private Double latitude;

    /**
     * 转换为商品实体。
     *
     * @return 商品实体
     */
    public Product toEntity() {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category == null ? "" : category);
        product.setPrice(price);
        product.setStock(stock == null ? 0 : stock);
        product.setLongitude(longitude);
        product.setLatitude(latitude);
        product.setStatus(ProductStatus.ON_SALE);
        return product;
    }

    /**
     * 获取名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取商品分类。
     *
     * @return 商品分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置商品分类。
     *
     * @param category 商品分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 获取商品价格。
     *
     * @return 商品价格
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 设置商品价格。
     *
     * @param price 商品价格
     */
    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    /**
     * 获取库存数量。
     *
     * @return 库存数量
     */
    public Integer getStock() {
        return stock;
    }

    /**
     * 设置库存数量。
     *
     * @param stock 库存数量
     */
    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * 获取门店经度。
     *
     * @return 门店经度
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * 设置门店经度。
     *
     * @param longitude 门店经度
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /**
     * 获取门店纬度。
     *
     * @return 门店纬度
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * 设置门店纬度。
     *
     * @param latitude 门店纬度
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

}
