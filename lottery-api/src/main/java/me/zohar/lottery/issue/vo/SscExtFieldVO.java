package me.zohar.lottery.issue.vo;

import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import me.zohar.lottery.issue.domain.Issue;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class SscExtFieldVO extends LotteryHistoryExtFieldVO {

	/**
	 * 大小
	 */
	private String bigSmall;

	/**
	 * 单双
	 */
	private String singleDouble;

	private String sumLotteryNum;

	private String sumBigSmall;

	private String sumSingleDouble;

	/**
	 * 龙虎
	 */
	private String loongTiger;

	public static SscExtFieldVO convertFor(Issue issue) {
		if (StrUtil.isBlank(issue.getLotteryNum())) {
			return new SscExtFieldVO();
		}
		List<String> bigSmall = new ArrayList<>();
		List<String> singleDouble = new ArrayList<>();
		long sumLotteryNum = 0L;
		long firstNum = 0L;
		long fifthNum = 0L;
		String[] lotteryNums = issue.getLotteryNum().split(",");
		for (int i = 0; i < lotteryNums.length; i++) {
			long lotteryNum = Long.parseLong(lotteryNums[i]);
			bigSmall.add(lotteryNum <= 4 ? "小" : "大");
			singleDouble.add(lotteryNum % 2 == 0 ? "双" : "单");
			sumLotteryNum += lotteryNum;
			if (i == 0) {
				firstNum = lotteryNum;
			}
			if (i == 4) {
				fifthNum = lotteryNum;
			}
		}
		int loongTigerCompare = Long.compare(firstNum, fifthNum);

		SscExtFieldVO vo = new SscExtFieldVO();
		vo.setBigSmall(String.join(",", bigSmall));
		vo.setSingleDouble(String.join(",", singleDouble));
		vo.setSumLotteryNum(String.valueOf(sumLotteryNum));
		vo.setSumBigSmall(sumLotteryNum <= 22 ? "小" : "大");
		vo.setSumSingleDouble(sumLotteryNum % 2 == 0 ? "双" : "单");
		vo.setLoongTiger(loongTigerCompare == 0 ? "和" : loongTigerCompare > 0 ? "龙" : "虎");
		return vo;
	}

}
