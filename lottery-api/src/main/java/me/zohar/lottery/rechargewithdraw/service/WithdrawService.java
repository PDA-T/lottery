package me.zohar.lottery.rechargewithdraw.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.validation.constraints.NotBlank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.common.vo.PageResult;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.rechargewithdraw.domain.WithdrawRecord;
import me.zohar.lottery.rechargewithdraw.param.LowerLevelWithdrawRecordQueryCondParam;
import me.zohar.lottery.rechargewithdraw.param.StartWithdrawParam;
import me.zohar.lottery.rechargewithdraw.param.WithdrawRecordQueryCondParam;
import me.zohar.lottery.rechargewithdraw.repo.WithdrawRecordRepo;
import me.zohar.lottery.rechargewithdraw.vo.WithdrawRecordVO;
import me.zohar.lottery.useraccount.domain.AccountChangeLog;
import me.zohar.lottery.useraccount.domain.UserAccount;
import me.zohar.lottery.useraccount.repo.AccountChangeLogRepo;
import me.zohar.lottery.useraccount.repo.UserAccountRepo;

@Service
public class WithdrawService {

	@Autowired
	private WithdrawRecordRepo withdrawRecordRepo;

	@Autowired
	private UserAccountRepo userAccountRepo;

	@Autowired
	private AccountChangeLogRepo accountChangeLogRepo;

	/**
	 * ????????????
	 * 
	 * @param id
	 */
	@ParamValid
	@Transactional
	public void approved(@NotBlank String id, String note) {
		WithdrawRecord withdrawRecord = withdrawRecordRepo.getOne(id);
		if (!Constant.??????????????????_????????????.equals(withdrawRecord.getState())) {
			throw new BizException(BizError.??????????????????????????????????????????????????????);
		}

		withdrawRecord.approved(note);
		withdrawRecordRepo.save(withdrawRecord);
	}

	/**
	 * ???????????????
	 * 
	 * @param id
	 */
	@ParamValid
	@Transactional
	public void notApproved(@NotBlank String id, String note) {
		WithdrawRecord withdrawRecord = withdrawRecordRepo.getOne(id);
		if (!(Constant.??????????????????_????????????.equals(withdrawRecord.getState())
				|| Constant.??????????????????_????????????.equals(withdrawRecord.getState()))) {
			throw new BizException(BizError.????????????????????????????????????????????????????????????????????????????????????);
		}

		withdrawRecord.notApproved(note);
		withdrawRecordRepo.save(withdrawRecord);
	}

	/**
	 * ????????????
	 * 
	 * @param id
	 */
	@ParamValid
	@Transactional
	public void confirmCredited(@NotBlank String id) {
		WithdrawRecord withdrawRecord = withdrawRecordRepo.getOne(id);
		if (!(Constant.??????????????????_????????????.equals(withdrawRecord.getState()))) {
			throw new BizException(BizError.??????????????????????????????????????????????????????????????????);
		}

		withdrawRecord.confirmCredited();
		withdrawRecordRepo.save(withdrawRecord);
	}

	@Transactional(readOnly = true)
	public PageResult<WithdrawRecordVO> findWithdrawRecordByPage(WithdrawRecordQueryCondParam param) {
		Specification<WithdrawRecord> spec = new Specification<WithdrawRecord>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<WithdrawRecord> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				if (StrUtil.isNotBlank(param.getOrderNo())) {
					predicates.add(builder.equal(root.get("orderNo"), param.getOrderNo()));
				}
				if (StrUtil.isNotBlank(param.getState())) {
					predicates.add(builder.equal(root.get("state"), param.getState()));
				}
				if (param.getSubmitStartTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("submitTime").as(Date.class),
							DateUtil.beginOfDay(param.getSubmitStartTime())));
				}
				if (param.getSubmitEndTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("submitTime").as(Date.class),
							DateUtil.endOfDay(param.getSubmitEndTime())));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<WithdrawRecord> result = withdrawRecordRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("submitTime"))));
		PageResult<WithdrawRecordVO> pageResult = new PageResult<>(WithdrawRecordVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

	@ParamValid
	@Transactional
	public void startWithdraw(StartWithdrawParam param) {
		UserAccount userAccount = userAccountRepo.getOne(param.getUserAccountId());
		if (!new BCryptPasswordEncoder().matches(param.getMoneyPwd(), userAccount.getMoneyPwd())) {
			throw new BizException(BizError.?????????????????????);
		}
		double balance = NumberUtil.round(userAccount.getBalance() - param.getWithdrawAmount(), 4).doubleValue();
		if (balance < 0) {
			throw new BizException(BizError.????????????);
		}
		if (userAccount.getBankInfoLatelyModifyTime() == null) {
			throw new BizException(BizError.????????????????????????????????????);
		}

		WithdrawRecord withdrawRecord = param.convertToPo();
		withdrawRecord.setBankInfo(userAccount);
		withdrawRecordRepo.save(withdrawRecord);

		userAccount.setBalance(balance);
		userAccountRepo.save(userAccount);
		accountChangeLogRepo.save(AccountChangeLog.buildWithStartWithdraw(userAccount, withdrawRecord));
	}
	
	@Transactional(readOnly = true)
	public PageResult<WithdrawRecordVO> findLowerLevelWithdrawRecordByPage(LowerLevelWithdrawRecordQueryCondParam param) {
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
		
		Specification<WithdrawRecord> spec = new Specification<WithdrawRecord>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			public Predicate toPredicate(Root<WithdrawRecord> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
				List<Predicate> predicates = new ArrayList<Predicate>();
				predicates.add(builder.like(root.join("userAccount", JoinType.INNER).get("accountLevelPath"),
						lowerLevelAccountLevelPath + "%"));
				if (StrUtil.isNotEmpty(param.getAccountType())) {
					predicates.add(builder.equal(root.join("userAccount", JoinType.INNER).get("accountType"),
							param.getAccountType()));
				}
				if (StrUtil.isNotBlank(param.getState())) {
					predicates.add(builder.equal(root.get("state"), param.getState()));
				}
				if (param.getSubmitStartTime() != null) {
					predicates.add(builder.greaterThanOrEqualTo(root.get("submitTime").as(Date.class),
							DateUtil.beginOfDay(param.getSubmitStartTime())));
				}
				if (param.getSubmitEndTime() != null) {
					predicates.add(builder.lessThanOrEqualTo(root.get("submitTime").as(Date.class),
							DateUtil.endOfDay(param.getSubmitEndTime())));
				}
				return predicates.size() > 0 ? builder.and(predicates.toArray(new Predicate[predicates.size()])) : null;
			}
		};
		Page<WithdrawRecord> result = withdrawRecordRepo.findAll(spec,
				PageRequest.of(param.getPageNum() - 1, param.getPageSize(), Sort.by(Sort.Order.desc("submitTime"))));
		PageResult<WithdrawRecordVO> pageResult = new PageResult<>(WithdrawRecordVO.convertFor(result.getContent()),
				param.getPageNum(), param.getPageSize(), result.getTotalElements());
		return pageResult;
	}

}
