package me.zohar.lottery.betting.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import me.zohar.lottery.agent.domain.RebateAndOdds;
import me.zohar.lottery.agent.repo.RebateAndOddsRepo;
import me.zohar.lottery.betting.domain.BettingOrder;
import me.zohar.lottery.betting.domain.BettingRebate;
import me.zohar.lottery.betting.domain.BettingRecord;
import me.zohar.lottery.betting.param.BettingOrderQueryCondParam;
import me.zohar.lottery.betting.param.BettingRecordParam;
import me.zohar.lottery.betting.param.ChangeOrderParam;
import me.zohar.lottery.betting.param.LowerLevelBettingOrderQueryCondParam;
import me.zohar.lottery.betting.param.PlaceOrderParam;
import me.zohar.lottery.betting.repo.BettingOrderRepo;
import me.zohar.lottery.betting.repo.BettingRebateRepo;
import me.zohar.lottery.betting.repo.BettingRecordRepo;
import me.zohar.lottery.betting.vo.BettingOrderDetailsVO;
import me.zohar.lottery.betting.vo.BettingOrderInfoVO;
import me.zohar.lottery.betting.vo.BettingRecordVO;
import me.zohar.lottery.betting.vo.WinningRankVO;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.utils.ThreadPoolUtils;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.common.vo.PageResult;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.game.domain.GamePlay;
import me.zohar.lottery.game.repo.GamePlayRepo;
import me.zohar.lottery.issue.domain.Issue;
import me.zohar.lottery.issue.enums.GamePlayEnum;
import me.zohar.lottery.issue.repo.IssueRepo;
import me.zohar.lottery.useraccount.domain.AccountChangeLog;
import me.zohar.lottery.useraccount.domain.UserAccount;
import me.zohar.lottery.useraccount.repo.AccountChangeLogRepo;
import me.zohar.lottery.useraccount.repo.UserAccountRepo;

@Validated
@Service
@Slf4j
public class BettingService {

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private BettingOrderRepo bettingOrderRepo;

	@Autowired
	private BettingRecordRepo bettingRecordRepo;

	@Autowired
	private UserAccountRepo userAccountRepo;

	@Autowired
	private AccountChangeLogRepo accountChangeLogRepo;

	@Autowired
	private GamePlayRepo gamePlayRepo;

	@Autowired
	private IssueRepo issueRepo;

	@Autowired
	private BettingRebateRepo bettingRebateRepo;

	@Autowired
	private RebateAndOddsRepo rebateAndOddsRepo;

	@Transactional(readOnly = true)
	public List<WinningRankVO> findTop50WinningRank() {
		List<BettingOrder> bettingOrders = bettingOrderRepo
				.findTop50ByBettingTimeGreaterThanAndStateOrderByTotalWinningAmountDesc(DateUtil.beginOfDay(new Date()),
						Constant.??????????????????_?????????);
		if (bettingOrders.size() < 50) {
			bettingOrders = bettingOrderRepo.findTop50ByStateOrderByTotalWinningAmountDesc(Constant.??????????????????_?????????);
		}
		return WinningRankVO.convertFor(bettingOrders);
	}

	@Transactional(readOnly = true)
	public BettingOrderDetailsVO findMyOrLowerLevelBettingOrderDetails(String id, String userAccountId) {
		BettingOrderDetailsVO vo = findBettingOrderDetails(id);
		UserAccount bettingOrderAccount = userAccountRepo.getOne(vo.getUserAccountId());
		UserAccount currentAccount = userAccountRepo.getOne(userAccountId);
		// ??????????????????????????????????????????????????????
		if (!bettingOrderAccount.getAccountLevelPath().startsWith(currentAccount.getAccountLevelPath())) {
			throw new BizException(BizError.????????????????????????);
		}
		return vo;
	}

	@Transactional(readOnly = true)
	public BettingOrderDetailsVO findBettingOrderDetails(String id) {
		BettingOrder bettingOrder = bettingOrderRepo.getOne(id);
		return BettingOrderDetailsVO.convertFor(bettingOrder);
	}

	/**
	 * ????????????????????????????????????
	 * 
	 * @param param
	 * @return
	 */
	@Transactional(readOnly = true)
	public PageResult<BettingOrderInfoVO> findMyBettingOrderInfoByPage(BettingOrderQueryCondParam param) {
		if (StrUtil.isBlank(param.getUserAccountId())) {
			throw new BizException(BizError.????????????????????????);
		}
		return findBettingOrderInfoByPage(param);
	}

	/**
	 * ??????????????????????????????
	 * 
	 * @param param
	 * @return
	 */
	@Transactional(readOnly = true)
	public PageResult<BettingOrderInfoVO> findBettingOrderInfoByPage(BettingOrderQueryCondParam param) {
		Specification<BettingOrder> spec = new Specification<BettingOrder>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<BettingOrder> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (StrUtil.isNotEmpty(param.getOrderNo())) {
					predicates.add(builder.equal(root.get("orderNo"), param.getOrderNo()));
				}
				if (StrUtil.isNotEmpty(param.getGameCode())) {
					predicates.add(builder.equal(root.get("gameCode"), param.getGameCode()));
				}
				if (param.getStartTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("bettingTime").as(Date.class),
							DateUtil.beginOfDay(param.getStartTime())));
				}
				if (param.getEndTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("bettingTime").as(Date.class),
							DateUtil.endOfDay(param.getEndTime())));
				}
				if (StrUtil.isNotEmpty(param.getState())) {
					predicates.add(builder.equal(root.get("state"), param.getState()));
				}
				if (StrUtil.isNotEmpty(param.getUserAccountId())) {
					predicates.add(builder.equal(root.get("userAccountId"), param.getUserAccountId()));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<BettingOrder> result = bettingOrderRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("bettingTime"))));
		PageResult<BettingOrderInfoVO> pageResult = new PageResult<>(BettingOrderInfoVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

	/**
	 * ??????????????????5???????????????
	 */
	@Transactional(readOnly = false)
	public List<BettingRecordVO> findTodayLatestThe5TimeBettingRecord(String userAccountId, String gameCode) {
		Date bettingTime = DateUtil.beginOfDay(new Date());
		List<BettingRecord> bettingRecords = bettingRecordRepo
				.findTop5ByBettingOrder_UserAccountIdAndBettingOrder_GameCodeAndBettingOrder_BettingTimeGreaterThanEqualOrderByBettingOrder_BettingTimeDesc(
						userAccountId, gameCode, bettingTime);
		return BettingRecordVO.convertFor(bettingRecords);
	}

	@ParamValid
	@Transactional
	public String placeOrder(PlaceOrderParam placeOrderParam, String userAccountId) {
		Date now = new Date();
		Issue currentIssue = issueRepo.findTopByGameCodeAndStartTimeLessThanEqualAndEndTimeGreaterThan(
				placeOrderParam.getGameCode(), now, now);
		Issue bettingIssue = issueRepo.findByGameCodeAndIssueNum(placeOrderParam.getGameCode(),
				placeOrderParam.getIssueNum());
		if (currentIssue == null) {
			throw new BizException(BizError.?????????);
		}
		if (currentIssue.getIssueNum() == placeOrderParam.getIssueNum()) {
			long second = DateUtil.between(currentIssue.getEndTime(), now, DateUnit.SECOND);
			if (second <= 30) {
				throw new BizException(BizError.???????????????);
			}
		} else {
			if (bettingIssue == null) {
				throw new BizException(BizError.????????????);
			}
			if (bettingIssue.getLotteryDate().getTime() < currentIssue.getLotteryDate().getTime()) {
				throw new BizException(BizError.????????????);
			}
			if (bettingIssue.getLotteryDate().getTime() > currentIssue.getLotteryDate().getTime()) {
				throw new BizException(BizError.?????????????????????);
			}
			if (bettingIssue.getIssueNum() < currentIssue.getIssueNum()) {
				throw new BizException(BizError.???????????????????????????);
			}
		}

		UserAccount userAccount = userAccountRepo.getOne(userAccountId);
		long totalBettingCount = 0;
		double totalBettingAmount = 0;
		List<BettingRecord> bettingRecords = new ArrayList<>();
		for (BettingRecordParam bettingRecordParam : placeOrderParam.getBettingRecords()) {
			GamePlay gamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(placeOrderParam.getGameCode(),
					bettingRecordParam.getGamePlayCode());
			if (gamePlay == null) {
				throw new BizException(BizError.?????????????????????);
			}
			if (Constant.??????????????????_??????.equals(gamePlay.getState())) {
				throw new BizException(BizError.?????????????????????);
			}
			Double odds = gamePlay.getOdds();
			Double accountOdds = userAccount.getOdds();
			if (odds == null || odds <= 0) {
				throw new BizException(BizError.??????????????????);
			}
			if (placeOrderParam.getRebate() > userAccount.getRebate()) {
				throw new BizException(BizError.???????????????????????????????????????);
			}
			if (placeOrderParam.getRebate() > 0) {
				RebateAndOdds rebateAndOdds = rebateAndOddsRepo.findTopByRebate(
						NumberUtil.round(userAccount.getRebate() - placeOrderParam.getRebate(), 4).doubleValue());
				if (rebateAndOdds == null) {
					throw new BizException(BizError.????????????????????????);
				}
				accountOdds = rebateAndOdds.getOdds();
			}

			odds = NumberUtil.round(odds * accountOdds, 4).doubleValue();
			double bettingAmount = NumberUtil.round(bettingRecordParam.getBettingCount()
					* placeOrderParam.getBaseAmount() * placeOrderParam.getMultiple(), 4).doubleValue();
			bettingRecords.add(bettingRecordParam.convertToPo(bettingAmount, odds));
			totalBettingCount += bettingRecordParam.getBettingCount();
			totalBettingAmount += bettingAmount;
		}
		double balance = NumberUtil.round(userAccount.getBalance() - totalBettingAmount, 4).doubleValue();
		if (userAccount.getBalance() <= 0 || balance < 0) {
			throw new BizException(BizError.????????????);
		}

		BettingOrder bettingOrder = placeOrderParam.convertToPo(bettingIssue.getId(), totalBettingCount,
				totalBettingAmount, userAccountId);
		bettingOrderRepo.save(bettingOrder);
		for (BettingRecord bettingRecord : bettingRecords) {
			bettingRecord.setBettingOrderId(bettingOrder.getId());
			bettingRecordRepo.save(bettingRecord);
		}
		userAccount.setBalance(balance);
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithPlaceOrder(userAccount, bettingOrder));
		return bettingOrder.getId();
	}

	/**
	 * ??????
	 */
	@ParamValid
	@Transactional
	public void changeOrder(List<ChangeOrderParam> params) {
		for (ChangeOrderParam param : params) {
			BettingOrder bettingOrder = bettingOrderRepo.getOne(param.getBettingOrderId());
			UserAccount userAccount = bettingOrder.getUserAccount();
			GamePlay gamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(bettingOrder.getGameCode(),
					param.getGamePlayCode());
			if (gamePlay == null) {
				throw new BizException(BizError.?????????????????????);
			}
			Double odds = gamePlay.getOdds();
			if (odds == null || odds <= 0) {
				throw new BizException(BizError.??????????????????);
			}
			odds = NumberUtil.round(odds * userAccount.getOdds(), 4).doubleValue();

			BettingRecord bettingRecord = bettingRecordRepo.getOne(param.getBettingRecordId());
			bettingRecord.setGamePlayCode(gamePlay.getGamePlayCode());
			bettingRecord.setOdds(odds);
			bettingRecord.setSelectedNo(param.getSelectedNo());
			bettingRecordRepo.save(bettingRecord);
		}
	}

	/**
	 * ??????
	 */
	@Transactional
	public void settlement(@NotBlank String issueId) {
		Issue issue = issueRepo.getOne(issueId);
		if (issue == null || StrUtil.isEmpty(issue.getLotteryNum())) {
			log.error("????????????????????????;id:{},issueNum:{}", issue.getId(), issue.getLotteryNum());
			return;
		}

		List<BettingOrder> bettingOrders = bettingOrderRepo.findByGameCodeAndIssueNumAndState(issue.getGameCode(),
				issue.getIssueNum(), Constant.??????????????????_?????????);
		for (BettingOrder bettingOrder : bettingOrders) {
			String state = Constant.??????????????????_?????????;
			double totalWinningAmount = 0;
			Set<BettingRecord> bettingRecords = bettingOrder.getBettingRecords();
			for (BettingRecord bettingRecord : bettingRecords) {
				GamePlayEnum gamePlay = GamePlayEnum
						.getPlay(bettingOrder.getGameCode() + "_" + bettingRecord.getGamePlayCode());
				int winningCount = gamePlay.calcWinningCount(issue.getLotteryNum(), bettingRecord.getSelectedNo());
				if (winningCount > 0) {
					double winningAmount = (bettingRecord.getBettingAmount() * bettingRecord.getOdds() * winningCount);
					bettingRecord.setWinningAmount(NumberUtil.round(winningAmount, 4).doubleValue());
					bettingRecord.setProfitAndLoss(
							NumberUtil.round(winningAmount - bettingRecord.getBettingAmount(), 4).doubleValue());
					bettingRecordRepo.save(bettingRecord);
					state = Constant.??????????????????_?????????;
					totalWinningAmount += winningAmount;
				}
			}
			bettingOrder.setLotteryNum(issue.getLotteryNum());
			bettingOrder.setState(state);

			if (Constant.??????????????????_?????????.equals(state)) {
				bettingOrderRepo.save(bettingOrder);
			} else {
				bettingOrder.setTotalWinningAmount(NumberUtil.round(totalWinningAmount, 4).doubleValue());
				bettingOrder.setTotalProfitAndLoss(
						NumberUtil.round(totalWinningAmount - bettingOrder.getTotalBettingAmount(), 4).doubleValue());
				bettingOrderRepo.save(bettingOrder);
				UserAccount userAccount = bettingOrder.getUserAccount();
				double balance = userAccount.getBalance() + totalWinningAmount;
				userAccount.setBalance(NumberUtil.round(balance, 4).doubleValue());
				userAccountRepo.save(userAccount);
				accountChangeLogRepo.save(AccountChangeLog.buildWithWinning(userAccount, bettingOrder));
			}
			generateBettingRebate(bettingOrder);
		}
		ThreadPoolUtils.getBettingRebateSettlementPool().schedule(() -> {
			redisTemplate.opsForList().leftPush(Constant.??????????????????ID, issueId);
		}, 1, TimeUnit.SECONDS);
	}

	/**
	 * ??????????????????
	 * 
	 * @param bettingOrder
	 */
	public void generateBettingRebate(BettingOrder bettingOrder) {
		// ?????????????????????
		if (bettingOrder.getRebateAmount() > 0) {
			BettingRebate bettingRebate = BettingRebate.build(bettingOrder.getRebate(), false,
					bettingOrder.getRebateAmount(), bettingOrder.getId(), bettingOrder.getUserAccountId());
			bettingRebateRepo.save(bettingRebate);
		}
		UserAccount userAccount = bettingOrder.getUserAccount();
		UserAccount superior = bettingOrder.getUserAccount().getInviter();
		while (superior != null) {
			double rebate = NumberUtil.round(superior.getRebate() - userAccount.getRebate(), 4).doubleValue();
			if (rebate < 0) {
				log.error("??????????????????,?????????????????????????????????????????????;????????????id:{},????????????id:{}", userAccount.getId(), superior.getId());
				break;
			}
			double rebateAmount = NumberUtil.round(rebate * 0.01 * bettingOrder.getTotalBettingAmount(), 4)
					.doubleValue();
			BettingRebate bettingRebate = BettingRebate.build(rebate, false, rebateAmount, bettingOrder.getId(),
					superior.getId());
			bettingRebateRepo.save(bettingRebate);
			if (Constant.??????????????????_?????????.equals(bettingOrder.getState())) {
				double winningRebateAmount = NumberUtil.round(rebate * 0.01 * bettingOrder.getTotalWinningAmount(), 4)
						.doubleValue();
				BettingRebate winningRebate = BettingRebate.build(rebate, true, winningRebateAmount,
						bettingOrder.getId(), superior.getId());
				bettingRebateRepo.save(winningRebate);
			}
			userAccount = superior;
			superior = superior.getInviter();
		}
	}

	/**
	 * ??????????????????
	 */
	@Transactional
	public void bettingRebateSettlement(@NotBlank String bettingRebateId) {
		BettingRebate bettingRebate = bettingRebateRepo.getOne(bettingRebateId);
		if (bettingRebate.getSettlementTime() != null) {
			log.warn("????????????????????????????????????,??????????????????;id:{}", bettingRebateId);
			return;
		}
		bettingRebate.settlement();
		bettingRebateRepo.save(bettingRebate);
		UserAccount userAccount = bettingRebate.getRebateAccount();
		double balance = userAccount.getBalance() + bettingRebate.getRebateAmount();
		userAccount.setBalance(NumberUtil.round(balance, 4).doubleValue());
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithBettingRebate(userAccount, bettingRebate));
	}

	/**
	 * ???????????????????????????????????????
	 * 
	 * @param issueId
	 */
	@Transactional(readOnly = true)
	public void noticeIssueRebateSettlement(@NotBlank String issueId) {
		Issue issue = issueRepo.getOne(issueId);
		List<BettingOrder> bettingOrders = bettingOrderRepo.findByGameCodeAndIssueNum(issue.getGameCode(),
				issue.getIssueNum());
		for (BettingOrder bettingOrder : bettingOrders) {
			List<BettingRebate> bettingRebates = bettingRebateRepo.findByBettingOrderId(bettingOrder.getId());
			for (BettingRebate bettingRebate : bettingRebates) {
				redisTemplate.opsForList().leftPush(Constant.????????????ID, bettingRebate.getId());
			}
		}
	}

	@Transactional(readOnly = true)
	public void bettingRebateAutoSettlement() {
		List<BettingRebate> bettingRebates = bettingRebateRepo.findBySettlementTimeIsNull();
		for (BettingRebate bettingRebate : bettingRebates) {
			redisTemplate.opsForList().leftPush(Constant.????????????ID, bettingRebate.getId());
		}
	}

	/**
	 * ??????
	 */
	@Transactional
	public void cancelOrder(@NotBlank String orderId, @NotBlank String userAccountId) {
		BettingOrder order = bettingOrderRepo.getOne(orderId);
		if (order == null) {
			throw new BizException(BizError.?????????????????????);
		}
		UserAccount currentAccount = userAccountRepo.getOne(userAccountId);
		if (!Constant.????????????_?????????.equals(currentAccount.getAccountType())
				&& !order.getUserAccountId().equals(userAccountId)) {
			throw new BizException(BizError.????????????????????????);
		}
		if (!Constant.??????????????????_?????????.equals(order.getState())) {
			throw new BizException(BizError.?????????????????????????????????);
		}
		Issue issue = issueRepo.findByGameCodeAndIssueNum(order.getGameCode(), order.getIssueNum());
		if (new Date().getTime() > issue.getEndTime().getTime()) {
			throw new BizException(BizError.???????????????????????????);
		}

		order.cancelOrder();
		UserAccount userAccount = order.getUserAccount();
		double balance = userAccount.getBalance() + order.getTotalBettingAmount();
		userAccount.setBalance(NumberUtil.round(balance, 4).doubleValue());
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithBettingCancelOrder(userAccount, order));
	}

	@Transactional
	public void batchCancelOrder(@NotEmpty List<String> orderIds, @NotBlank String userAccountId) {
		for (String orderId : orderIds) {
			cancelOrder(orderId, userAccountId);
		}
	}

	/**
	 * ??????????????????????????????????????????
	 * 
	 * @param param
	 * @return
	 */
	@Transactional(readOnly = true)
	public PageResult<BettingOrderInfoVO> findLowerLevelBettingOrderInfoByPage(
			LowerLevelBettingOrderQueryCondParam param) {
		UserAccount currentAccount = userAccountRepo.getOne(param.getCurrentAccountId());
		UserAccount lowerLevelAccount = currentAccount;
		if (StrUtil.isNotBlank(param.getUserName())) {
			lowerLevelAccount = userAccountRepo.findByUserName(param.getUserName());
			if (lowerLevelAccount == null) {
				throw new BizException(BizError.??????????????????);
			}
			// ??????????????????????????????????????????????????????????????????
			if (!lowerLevelAccount.getAccountLevelPath().startsWith(currentAccount.getAccountLevelPath())) {
				throw new BizException(BizError.???????????????????????????????????????????????????????????????);
			}
		}
		String lowerLevelAccountLevelPath = lowerLevelAccount.getAccountLevelPath();

		Specification<BettingOrder> spec = new Specification<BettingOrder>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<BettingOrder> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				predicates.add(builder.like(root.join("userAccount", JoinType.INNER).get("accountLevelPath"),
						lowerLevelAccountLevelPath + "%"));
				if (StrUtil.isNotEmpty(param.getAccountType())) {
					predicates.add(builder.equal(root.join("userAccount", JoinType.INNER).get("accountType"),
							param.getAccountType()));
				}
				if (StrUtil.isNotEmpty(param.getGameCode())) {
					predicates.add(builder.equal(root.get("gameCode"), param.getGameCode()));
				}
				if (param.getIssueNum() != null) {
					predicates.add(builder.equal(root.get("issueNum"), param.getIssueNum()));
				}
				if (param.getStartTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("bettingTime").as(Date.class),
							DateUtil.beginOfDay(param.getStartTime())));
				}
				if (param.getEndTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("bettingTime").as(Date.class),
							DateUtil.endOfDay(param.getEndTime())));
				}
				if (StrUtil.isNotEmpty(param.getState())) {
					predicates.add(builder.equal(root.get("state"), param.getState()));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<BettingOrder> result = bettingOrderRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("bettingTime"))));
		PageResult<BettingOrderInfoVO> pageResult = new PageResult<>(BettingOrderInfoVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

}
