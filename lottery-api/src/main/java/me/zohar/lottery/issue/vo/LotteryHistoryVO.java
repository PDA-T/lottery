package me.zohar.lottery.issue.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;

import com.fasterxml.jackson.annotation.JsonFormat;

import cn.hutool.core.collection.CollectionUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.zohar.lottery.issue.domain.Issue;

/**
 * 开奖历史
 * 
 * @author zohar
 * @date 2019年6月16日
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LotteryHistoryVO {

	/**
	 * 主键id
	 */
	private String id;

	/**
	 * 期数
	 */
	private Long issueNum;

	/**
	 * 开奖时间
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private Date lotteryTime;

	/**
	 * 全部开奖号码,以逗号分隔
	 */
	private String lotteryNum;

	/**
	 * 扩展字段
	 */
	private LotteryHistoryExtFieldVO extField;

	public static List<LotteryHistoryVO> convertFor(List<Issue> issues) {
		if (CollectionUtil.isEmpty(issues)) {
			return new ArrayList<>();
		}
		List<LotteryHistoryVO> vos = new ArrayList<>();
		for (Issue issue : issues) {
			vos.add(convertFor(issue));
		}
		return vos;
	}

	public static LotteryHistoryVO convertFor(Issue issue) {
		if (issue == null) {
			return null;
		}
		LotteryHistoryVO vo = new LotteryHistoryVO();
		BeanUtils.copyProperties(issue, vo);
		vo.setExtField(SscExtFieldVO.convertFor(issue));
		return vo;
	}

}
