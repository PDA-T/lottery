package me.zohar.lottery.issue.service;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import com.alibaba.fastjson.JSON;
import com.xxl.mq.client.message.XxlMqMessage;
import com.xxl.mq.client.producer.XxlMqProducer;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import me.zohar.lottery.betting.service.BettingService;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.utils.IdUtils;
import me.zohar.lottery.common.utils.ThreadPoolUtils;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.issue.domain.Issue;
import me.zohar.lottery.issue.domain.IssueGenerateRule;
import me.zohar.lottery.issue.domain.IssueSetting;
import me.zohar.lottery.issue.param.IssueEditParam;
import me.zohar.lottery.issue.param.LotteryHistoryParam;
import me.zohar.lottery.issue.param.ManualLotteryParam;
import me.zohar.lottery.issue.param.SyncLotteryNumMsg;
import me.zohar.lottery.issue.repo.IssueRepo;
import me.zohar.lottery.issue.repo.IssueSettingRepo;
import me.zohar.lottery.issue.vo.IssueVO;
import me.zohar.lottery.issue.vo.LotteryHistoryVO;

@Validated
@Service
@Slf4j
public class IssueService {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private BettingService bettingService;

	@Autowired
	private IssueRepo issueRepo;

	@Autowired
	private IssueSettingRepo issueSettingRepo;

	/**
	 * ??????????????????
	 * 
	 * @param gameCode
	 * @param issueNum
	 * @param lotteryNum
	 */
	@Transactional
	public void syncLotteryNum(@NotBlank String gameCode, @NotNull Long issueNum, @NotBlank String lotteryNum) {
		Issue issue = issueRepo.findByGameCodeAndIssueNum(gameCode, issueNum);
		if (issue == null) {
			log.error("????????????????????????,??????????????????????????????????????????.gameCode:{},issueNum:{}", gameCode, issueNum);
			return;
		}
		if (!Constant.????????????_?????????.equals(issue.getState())) {
			return;
		}
		if (!issue.getAutomaticLottery()) {
			log.warn("??????????????????????????????????????????,????????????????????????.gameCode:{},issueNum:{}", gameCode, issueNum);
			return;
		}

		issue.syncLotteryNum(lotteryNum);
		issueRepo.save(issue);
		if (issue.getAutomaticSettlement()) {
			ThreadPoolUtils.getLotterySettlementPool().schedule(() -> {
				redisTemplate.opsForList().leftPush(Constant.??????????????????ID, issue.getId());
			}, 1, TimeUnit.SECONDS);
		}
	}

	/**
	 * ??????
	 */
	@Transactional
	public void settlement(String issueId) {
		Issue issue = issueRepo.getOne(issueId);
		if (issue == null || StrUtil.isEmpty(issue.getLotteryNum())) {
			log.error("????????????????????????;id:{},issueNum:{}", issue.getId(), issue.getLotteryNum());
			return;
		}
		issue.settlement();
		issueRepo.save(issue);
		bettingService.settlement(issueId);
	}

	/**
	 * ????????????5????????????????????????
	 * 
	 * @return
	 */
	public List<IssueVO> findLatelyThe5TimeIssue(String gameCode) {
		List<Issue> issues = issueRepo.findTop5ByGameCodeAndEndTimeLessThanOrderByIssueNumDesc(gameCode, new Date());
		return IssueVO.convertFor(issues);
	}

	/**
	 * ????????????50????????????????????????
	 * 
	 * @return
	 */
	public List<IssueVO> findLatelyThe50TimeIssue(String gameCode) {
		List<Issue> issues = issueRepo.findTop50ByGameCodeAndEndTimeLessThanOrderByIssueNumDesc(gameCode, new Date());
		return IssueVO.convertFor(issues);
	}

	/**
	 * ???????????????
	 * 
	 * @return
	 */
	public IssueVO getNextIssue(String gameCode) {
		Date now = new Date();
		Issue nextIssue = issueRepo.findTopByGameCodeAndStartTimeGreaterThanOrderByLotteryTimeAsc(gameCode, now);
		return IssueVO.convertFor(nextIssue);
	}

	/**
	 * ?????????????????????,??????????????????????????????
	 * 
	 * @return
	 */
	public IssueVO getCurrentIssue(String gameCode) {
		Date now = new Date();
		Issue currentIssue = issueRepo.findTopByGameCodeAndStartTimeLessThanEqualAndEndTimeGreaterThan(gameCode, now,
				now);
		return IssueVO.convertFor(currentIssue);
	}

	/**
	 * ?????????????????????,???????????????????????????????????????????????????????????????
	 * 
	 * @return
	 */
	public IssueVO getLatelyIssue(String gameCode) {
		IssueVO currentIssue = getCurrentIssue(gameCode);
		if (currentIssue == null) {
			Issue latelyIssue = issueRepo.findTopByGameCodeAndEndTimeLessThanEqualOrderByEndTimeDesc(gameCode,
					new Date());
			return IssueVO.convertFor(latelyIssue);
		}
		Issue latelyIssue = issueRepo.findTopByGameCodeAndIssueNumLessThanOrderByIssueNumDesc(gameCode,
				currentIssue.getIssueNum());
		return IssueVO.convertFor(latelyIssue);
	}

	@Transactional
	public void generateIssue(Date currentDate) {
		List<IssueSetting> issueSettings = issueSettingRepo.findAll();
		for (IssueSetting issueSetting : issueSettings) {
			for (int i = 0; i < 5; i++) {
				Date lotteryDate = DateUtil.offset(DateUtil.beginOfDay(currentDate), DateField.DAY_OF_MONTH, i);
				List<Issue> issues = issueRepo.findByGameCodeAndLotteryDateOrderByLotteryTimeDesc(
						issueSetting.getGame().getGameCode(), lotteryDate);
				if (CollectionUtil.isNotEmpty(issues)) {
					continue;
				}

				String lotteryDateFormat = DateUtil.format(lotteryDate, issueSetting.getDateFormat());
				Set<IssueGenerateRule> issueGenerateRules = issueSetting.getIssueGenerateRules();
				int count = 0;
				for (IssueGenerateRule issueGenerateRule : issueGenerateRules) {
					Integer issueCount = issueGenerateRule.getIssueCount();
					for (int j = 0; j < issueCount; j++) {
						long issueNum = Long.parseLong(
								lotteryDateFormat + String.format(issueSetting.getIssueFormat(), count + j + 1));
						long issueNumInner = count + j + 1;
						DateTime dateTime = DateUtil.parse(issueGenerateRule.getStartTime(), "hh:mm");
						Date startTime = DateUtil.offset(lotteryDate, DateField.MINUTE,
								dateTime.hour(true) * 60 + dateTime.minute() + j * issueGenerateRule.getTimeInterval());
						Date endTime = DateUtil.offset(startTime, DateField.MINUTE,
								issueGenerateRule.getTimeInterval());

						Issue issue = Issue.builder().id(IdUtils.getId()).gameCode(issueSetting.getGame().getGameCode())
								.lotteryDate(lotteryDate).lotteryTime(endTime).issueNum(issueNum)
								.issueNumInner(issueNumInner).startTime(startTime).endTime(endTime)
								.state(Constant.????????????_?????????).automaticLottery(true).automaticSettlement(true).build();
						issueRepo.save(issue);

						Date effectTime = DateUtil.offset(issue.getEndTime(), DateField.SECOND, 2);
						XxlMqProducer.produce(new XxlMqMessage("SYNC_LOTTERY_NUM_" + issue.getGameCode(),
								JSON.toJSONString(new SyncLotteryNumMsg(issue.getGameCode(), issue.getIssueNum(), 0)),
								effectTime));
					}
					count += issueCount;
				}
			}
		}
	}

	/**
	 * ????????????
	 * 
	 * @param id
	 * @param lotteryNum
	 */
	@ParamValid
	@Transactional
	public void manualLottery(ManualLotteryParam param) {
		Issue issue = issueRepo.getOne(param.getId());
		if (!Constant.????????????_?????????.equals(issue.getState())) {
			throw new BizException(BizError.???????????????);
		}
		issue.syncLotteryNum(param.getLotteryNum());
		issueRepo.save(issue);

		if (param.getAutoSettlementFlag()) {
			redisTemplate.opsForList().leftPush(Constant.??????????????????ID, param.getId());
		}
	}

	/**
	 * ????????????
	 * 
	 * @param id
	 */
	@Transactional(readOnly = true)
	public void manualSettlement(String id) {
		Issue issue = issueRepo.getOne(id);
		if (!Constant.????????????_?????????.equals(issue.getState())) {
			throw new BizException(BizError.?????????????????????);
		}
		redisTemplate.opsForList().leftPush(Constant.??????????????????ID, id);
	}

	@ParamValid
	@Transactional
	public void updateIssue(IssueEditParam param) {
		Issue issue = issueRepo.getOne(param.getId());
		if (param.getIssueInvalid()
				&& (Constant.????????????_?????????.equals(issue.getState()) || Constant.????????????_?????????.equals(issue.getState()))) {
			throw new BizException(BizError.??????????????????);
		}
		if ((Constant.????????????_?????????.equals(issue.getState()) || Constant.????????????_?????????.equals(issue.getState()))) {
			issue.setState(param.getIssueInvalid() ? Constant.????????????_????????? : Constant.????????????_?????????);
		}
		issue.setAutomaticLottery(param.getAutomaticLottery());
		issue.setAutomaticSettlement(param.getAutomaticSettlement());
		issueRepo.save(issue);
	}

	/**
	 * ????????????????????????????????????
	 * 
	 * @return
	 */
	public List<IssueVO> findTodayTrackingNumberIssue(String gameCode) {
		IssueVO currentIssue = getCurrentIssue(gameCode);
		if (currentIssue == null) {
			return null;
		}
		List<Issue> issues = issueRepo.findByGameCodeAndLotteryDateAndLotteryTimeGreaterThanEqualOrderByLotteryTimeAsc(
				gameCode, currentIssue.getLotteryDate(), currentIssue.getLotteryTime());
		return IssueVO.convertFor(issues);
	}

	public IssueVO findByGameCodeAndIssueNum(String gameCode, Long issueNum) {
		return IssueVO.convertFor(issueRepo.findByGameCodeAndIssueNum(gameCode, issueNum));
	}

	@ParamValid
	public List<LotteryHistoryVO> findLotteryHistory(LotteryHistoryParam param) {
		List<Issue> issues = issueRepo.findByGameCodeAndLotteryDateAndEndTimeLessThanEqualOrderByEndTimeDesc(
				param.getGameCode(), param.getLotteryDate(), new Date());
		return LotteryHistoryVO.convertFor(issues);
	}

}
