package com.sky.mapper;

import com.sky.mapper.model.DishProfileRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 负责菜品推荐画像的新增、更新、查询和删除。
 */
@Mapper
public interface DishProfileMapper {

    /**
     * 新增或覆盖指定菜品的推荐画像。
     */
    @Insert("insert into dish_profile "
            + "(dish_id, spicy_level, sweetness_level, saltiness_level, oiliness_level, "
            + "calorie_level, tags, allergens, update_time) "
            + "values (#{dishId}, #{spicyLevel}, #{sweetnessLevel}, #{saltinessLevel}, "
            + "#{oilinessLevel}, #{calorieLevel}, cast(#{tagsJson} as json), "
            + "cast(#{allergensJson} as json), now()) "
            + "on duplicate key update spicy_level=values(spicy_level), "
            + "sweetness_level=values(sweetness_level), saltiness_level=values(saltiness_level), "
            + "oiliness_level=values(oiliness_level), calorie_level=values(calorie_level), "
            + "tags=values(tags), allergens=values(allergens), update_time=now()")
    void upsert(@Param("dishId") Long dishId,
                @Param("spicyLevel") Integer spicyLevel,
                @Param("sweetnessLevel") Integer sweetnessLevel,
                @Param("saltinessLevel") Integer saltinessLevel,
                @Param("oilinessLevel") Integer oilinessLevel,
                @Param("calorieLevel") Integer calorieLevel,
                @Param("tagsJson") String tagsJson,
                @Param("allergensJson") String allergensJson);

    /**
     * 查询指定菜品的推荐画像原始数据。
     */
    @Select("select dish_id, spicy_level, sweetness_level, saltiness_level, "
            + "oiliness_level, calorie_level, cast(tags as char) tags_json, "
            + "cast(allergens as char) allergens_json, update_time "
            + "from dish_profile where dish_id = #{dishId}")
    DishProfileRow getByDishId(Long dishId);

    /**
     * 删除指定菜品的推荐画像。
     */
    @Delete("delete from dish_profile where dish_id = #{dishId}")
    void deleteByDishId(Long dishId);
}
