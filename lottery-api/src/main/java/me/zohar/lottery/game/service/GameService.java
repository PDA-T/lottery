package me.zohar.lottery.game.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import cn.hutool.core.util.StrUtil;
import me.zohar.lottery.common.exception.BizError;
import me.zohar.lottery.common.exception.BizException;
import me.zohar.lottery.common.utils.IdUtils;
import me.zohar.lottery.common.valid.ParamValid;
import me.zohar.lottery.constants.Constant;
import me.zohar.lottery.dictconfig.domain.DictItem;
import me.zohar.lottery.dictconfig.domain.DictType;
import me.zohar.lottery.dictconfig.repo.DictItemRepo;
import me.zohar.lottery.dictconfig.repo.DictTypeRepo;
import me.zohar.lottery.game.domain.Game;
import me.zohar.lottery.game.domain.GameCategory;
import me.zohar.lottery.game.domain.GamePlay;
import me.zohar.lottery.game.domain.GameSituation;
import me.zohar.lottery.game.domain.NumLocate;
import me.zohar.lottery.game.domain.OptionalNum;
import me.zohar.lottery.game.param.GameCategoryParam;
import me.zohar.lottery.game.param.GameParam;
import me.zohar.lottery.game.param.GamePlayParam;
import me.zohar.lottery.game.param.NumLocateParam;
import me.zohar.lottery.game.param.OptionalNumParam;
import me.zohar.lottery.game.repo.GameCategoryRepo;
import me.zohar.lottery.game.repo.GamePlayRepo;
import me.zohar.lottery.game.repo.GameRepo;
import me.zohar.lottery.game.repo.GameSituationRepo;
import me.zohar.lottery.game.repo.NumLocateRepo;
import me.zohar.lottery.game.repo.OptionalNumRepo;
import me.zohar.lottery.game.vo.GameCategoryVO;
import me.zohar.lottery.game.vo.GamePlayVO;
import me.zohar.lottery.game.vo.GameSituationVO;
import me.zohar.lottery.game.vo.GameVO;
import me.zohar.lottery.issue.domain.IssueSetting;
import me.zohar.lottery.issue.repo.IssueGenerateRuleRepo;
import me.zohar.lottery.issue.repo.IssueSettingRepo;

@Validated
@Service
public class GameService {

	@Autowired
	private GameCategoryRepo gameCategoryRepo;

	@Autowired
	private GameRepo gameRepo;

	@Autowired
	private GameSituationRepo gameSituationRepo;

	@Autowired
	private GamePlayRepo gamePlayRepo;

	@Autowired
	private NumLocateRepo numLocateRepo;

	@Autowired
	private OptionalNumRepo optionalNumRepo;

	@Autowired
	private IssueSettingRepo issueSettingRepo;

	@Autowired
	private IssueGenerateRuleRepo issueGenerateRuleRepo;

	@Autowired
	private DictItemRepo dictItemRepo;

	@Autowired
	private DictTypeRepo dictTypeRepo;

	@Transactional(readOnly = true)
	public List<GameSituationVO> findTop5HotGame() {
		List<GameSituation> gameSituations = gameSituationRepo.findTop5ByOrderByHotGameFlagDesc();
		return GameSituationVO.convertFor(gameSituations);
	}

	@Transactional(readOnly = true)
	public List<GameSituationVO> findAllGameSituation() {
		List<GameSituation> gameSituations = gameSituationRepo.findByOrderByHotGameFlagDesc();
		return GameSituationVO.convertFor(gameSituations);
	}

	@Transactional(readOnly = true)
	public List<GameSituationVO> findGameSituationByGameCategoryId(String gameCategoryId) {
		List<GameSituation> gameSituations = gameSituationRepo
				.findByGameCategoryIdOrderByHotGameFlagDesc(gameCategoryId);
		return GameSituationVO.convertFor(gameSituations);
	}

	@Transactional
	public void dictSync(@NotNull Boolean syncGameDict, @NotNull Boolean syncGamePlayDict) {
		if (syncGameDict) {
			DictType dictType = dictTypeRepo.findByDictTypeCode("game");
			dictItemRepo.deleteAll(dictType.getDictItems());
			double orderNo = 1;
			List<Game> games = gameRepo.findAll();
			for (Game game : games) {
				DictItem dictItem = new DictItem();
				dictItem.setId(IdUtils.getId());
				dictItem.setDictItemCode(game.getGameCode());
				dictItem.setDictItemName(game.getGameName());
				dictItem.setDictTypeId(dictType.getId());
				dictItem.setOrderNo(orderNo);
				dictItemRepo.save(dictItem);
				orderNo++;
			}
		}
		if (syncGamePlayDict) {
			DictType dictType = dictTypeRepo.findByDictTypeCode("gamePlay");
			dictItemRepo.deleteAll(dictType.getDictItems());
			double orderNo = 1;
			List<GamePlay> gamePlays = gamePlayRepo.findAll();
			for (GamePlay gamePlay : gamePlays) {
				DictItem dictItem = new DictItem();
				dictItem.setId(IdUtils.getId());
				dictItem.setDictItemCode(gamePlay.getGameCode() + "_" + gamePlay.getGamePlayCode());
				dictItem.setDictItemName(gamePlay.getGamePlayName());
				dictItem.setDictTypeId(dictType.getId());
				dictItem.setOrderNo(orderNo);
				dictItemRepo.save(dictItem);
				orderNo++;
			}
		}
	}

	@Transactional(readOnly = true)
	public List<GameVO> findAllGame() {
		List<Game> games = gameRepo.findAll(Sort.by(Sort.Order.asc("orderNo")));
		return GameVO.convertFor(games);
	}

	@Transactional(readOnly = true)
	public List<GameVO> findGameByGameCategoryId(String gameCategoryId) {
		if (StrUtil.isBlank(gameCategoryId)) {
			return findAllGame();
		}
		return GameVO.convertFor(gameRepo.findByGameCategoryIdOrderByOrderNoAsc(gameCategoryId));
	}

	@Transactional(readOnly = true)
	public List<GameVO> findAllOpenGame() {
		List<Game> games = gameRepo.findByStateOrderByOrderNo(Constant.????????????_??????);
		return GameVO.convertFor(games);
	}

	@Transactional
	public void delGameById(String id) {
		Game game = gameRepo.getOne(id);
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(game.getGameCode());
		for (GamePlay gamePlay : gamePlays) {
			delGamePlayInner(gamePlay);
		}
		IssueSetting issueSetting = issueSettingRepo.findByGameId(game.getId());
		if (issueSetting != null) {
			issueGenerateRuleRepo.deleteAll(issueSetting.getIssueGenerateRules());
			issueSettingRepo.delete(issueSetting);
		}
		gameRepo.delete(game);
	}

	@Transactional(readOnly = true)
	public GameVO findGameById(String id) {
		Game game = gameRepo.getOne(id);
		return GameVO.convertFor(game);
	}

	@Transactional(readOnly = true)
	public GameVO findGameByGameCode(@NotBlank String gameCode) {
		Game game = gameRepo.findByGameCode(gameCode);
		return GameVO.convertFor(game);
	}

	@Transactional(readOnly = true)
	public List<GamePlayVO> findGamePlayByGameCode(String gameCode) {
		List<GamePlayVO> vos = new ArrayList<>();
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(gameCode);
		for (GamePlay gamePlay : gamePlays) {
			GamePlayVO vo = GamePlayVO.convertFor(gamePlay);
			vos.add(vo);
		}
		return vos;
	}

	@Transactional(readOnly = true)
	public List<GamePlayVO> findGamePlayDetailsByGameCode(String gameCode) {
		List<GamePlayVO> vos = new ArrayList<>();
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(gameCode);
		for (GamePlay gamePlay : gamePlays) {
			vos.add(GamePlayVO.buildGamePlayDetails(gamePlay));
		}
		return vos;
	}

	@Transactional(readOnly = true)
	public GamePlayVO findGamePlayDetailsById(String id) {
		GamePlay gamePlay = gamePlayRepo.getOne(id);
		return GamePlayVO.buildGamePlayDetails(gamePlay);
	}

	@Transactional
	public void updateGamePlayState(String id, String state) {
		GamePlay gamePlay = gamePlayRepo.getOne(id);
		gamePlay.setState(state);
		gamePlayRepo.save(gamePlay);
	}

	@Transactional
	public void delGamePlayById(String id) {
		GamePlay gamePlay = gamePlayRepo.getOne(id);
		delGamePlayInner(gamePlay);
	}

	@Transactional
	public void delGamePlayInner(GamePlay gamePlay) {
		Set<NumLocate> numLocates = gamePlay.getNumLocates();
		for (NumLocate numLocate : numLocates) {
			optionalNumRepo.deleteAll(numLocate.getOptionalNums());
		}
		numLocateRepo.deleteAll(numLocates);
		gamePlayRepo.delete(gamePlay);
	}

	@ParamValid
	@Transactional
	public void addOrUpdateGame(GameParam gameParam) {
		// ??????
		if (StrUtil.isBlank(gameParam.getId())) {
			Game existGame = gameRepo.findByGameCode(gameParam.getGameCode());
			if (existGame != null) {
				throw new BizException(BizError.?????????????????????);
			}
			Game game = gameParam.convertToPo();
			gameRepo.save(game);
			copyGamePlay(game, gameParam.getCopyGameCode());
		}
		// ??????
		else {
			Game existGame = gameRepo.findByGameCode(gameParam.getGameCode());
			if (existGame != null && !existGame.getId().equals(gameParam.getId())) {
				throw new BizException(BizError.?????????????????????);
			}
			Game game = gameRepo.getOne(gameParam.getId());
			BeanUtils.copyProperties(gameParam, game);
			gameRepo.save(game);
			copyGamePlay(game, gameParam.getCopyGameCode());
		}
	}

	@Transactional
	public void copyGamePlay(Game game, String copyGameCode) {
		if (StrUtil.isBlank(copyGameCode)) {
			return;
		}
		List<GamePlay> gamePlays = gamePlayRepo.findByGameCodeOrderByOrderNo(copyGameCode);
		for (GamePlay gamePlay : gamePlays) {
			GamePlay existGamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(game.getGameCode(),
					gamePlay.getGamePlayCode());
			if (existGamePlay != null) {
				continue;
			}

			GamePlay newGamePlay = new GamePlay();
			BeanUtils.copyProperties(gamePlay, newGamePlay);
			newGamePlay.setId(IdUtils.getId());
			newGamePlay.setGameCode(game.getGameCode());
			gamePlayRepo.save(newGamePlay);

			Set<NumLocate> numLocates = gamePlay.getNumLocates();
			for (NumLocate numLocate : numLocates) {
				NumLocate newNumLocate = new NumLocate();
				BeanUtils.copyProperties(numLocate, newNumLocate);
				newNumLocate.setId(IdUtils.getId());
				newNumLocate.setGamePlayId(newGamePlay.getId());
				numLocateRepo.save(newNumLocate);

				Set<OptionalNum> optionalNums = numLocate.getOptionalNums();
				for (OptionalNum optionalNum : optionalNums) {
					OptionalNum newOptionalNum = new OptionalNum();
					BeanUtils.copyProperties(optionalNum, newOptionalNum);
					newOptionalNum.setId(IdUtils.getId());
					newOptionalNum.setNumLocateId(newNumLocate.getId());
					optionalNumRepo.save(newOptionalNum);
				}

			}
		}
	}

	@ParamValid
	@Transactional
	public void addOrUpdateGamePlay(GamePlayParam gamePlayParam) {
		if (Constant.????????????_????????????.equals(gamePlayParam.getOddsMode())) {
			if (gamePlayParam.getOdds() == null) {
				throw new BizException(BizError.????????????.getCode(), "??????????????????");
			}
			if (gamePlayParam.getOdds() <= 0) {
				throw new BizException(BizError.????????????.getCode(), "???????????????????????????0");
			}
		} else {
			gamePlayParam.setOdds(0d);
		}

		// ??????
		if (StrUtil.isBlank(gamePlayParam.getId())) {
			GamePlay existGamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(gamePlayParam.getGameCode(),
					gamePlayParam.getGamePlayCode());
			if (existGamePlay != null) {
				throw new BizException(BizError.???????????????????????????);
			}
			GamePlay gamePlay = gamePlayParam.convertToPo();
			gamePlayRepo.save(gamePlay);
			for (int i = 0; i < gamePlayParam.getNumLocates().size(); i++) {
				NumLocateParam numLocateParam = gamePlayParam.getNumLocates().get(i);
				NumLocate numLocate = numLocateParam.convertToPo();
				numLocate.setOrderNo((double) (i + 1));
				numLocate.setGamePlayId(gamePlay.getId());
				numLocateRepo.save(numLocate);

				for (int j = 0; j < numLocateParam.getOptionalNums().size(); j++) {
					OptionalNumParam optionalNumParam = numLocateParam.getOptionalNums().get(j);
					OptionalNum optionalNum = optionalNumParam.convertToPo();
					optionalNum.setOrderNo((double) (j + 1));
					optionalNum.setNumLocateId(numLocate.getId());
					optionalNumRepo.save(optionalNum);
				}
			}
		}
		// ??????
		else {
			GamePlay existGamePlay = gamePlayRepo.findByGameCodeAndGamePlayCode(gamePlayParam.getGameCode(),
					gamePlayParam.getGamePlayCode());
			if (existGamePlay != null && !existGamePlay.getId().equals(gamePlayParam.getId())) {
				throw new BizException(BizError.???????????????????????????);
			}
			List<NumLocate> numLocates = numLocateRepo.findByGamePlayId(gamePlayParam.getId());
			for (NumLocate numLocate : numLocates) {
				optionalNumRepo.deleteAll(numLocate.getOptionalNums());
			}
			numLocateRepo.deleteAll(numLocates);

			GamePlay gamePlay = gamePlayRepo.getOne(gamePlayParam.getId());
			BeanUtils.copyProperties(gamePlayParam, gamePlay);
			gamePlayRepo.save(gamePlay);
			for (int i = 0; i < gamePlayParam.getNumLocates().size(); i++) {
				NumLocateParam numLocateParam = gamePlayParam.getNumLocates().get(i);
				NumLocate numLocate = numLocateParam.convertToPo();
				numLocate.setOrderNo((double) (i + 1));
				numLocate.setGamePlayId(gamePlay.getId());
				numLocateRepo.save(numLocate);

				for (int j = 0; j < numLocateParam.getOptionalNums().size(); j++) {
					OptionalNumParam optionalNumParam = numLocateParam.getOptionalNums().get(j);
					OptionalNum optionalNum = optionalNumParam.convertToPo();
					optionalNum.setOrderNo((double) (j + 1));
					optionalNum.setNumLocateId(numLocate.getId());
					optionalNumRepo.save(optionalNum);
				}
			}
		}
	}

	public List<GameCategoryVO> findAllGameCategory() {
		return GameCategoryVO.convertFor(gameCategoryRepo.findByOrderByOrderNo());
	}

	@ParamValid
	@Transactional
	public void addOrUpdateGameCategory(GameCategoryParam gameCategoryParam) {
		// ??????
		if (StrUtil.isBlank(gameCategoryParam.getId())) {
			GameCategory existGameCategory = gameCategoryRepo
					.findByGameCategoryCode(gameCategoryParam.getGameCategoryCode());
			if (existGameCategory != null) {
				throw new BizException(BizError.???????????????????????????);
			}
			GameCategory gameCategory = gameCategoryParam.convertToPo();
			gameCategoryRepo.save(gameCategory);
		}
		// ??????
		else {
			GameCategory existGameCategory = gameCategoryRepo
					.findByGameCategoryCode(gameCategoryParam.getGameCategoryCode());
			if (existGameCategory != null && !existGameCategory.getId().equals(gameCategoryParam.getId())) {
				throw new BizException(BizError.???????????????????????????);
			}
			GameCategory gameCategory = gameCategoryRepo.getOne(gameCategoryParam.getId());
			BeanUtils.copyProperties(gameCategoryParam, gameCategory);
			gameCategoryRepo.save(gameCategory);
		}
	}

	@Transactional
	public void delGameCategoryById(String id) {
		GameCategory gameCategory = gameCategoryRepo.getOne(id);
		for (Game game : gameCategory.getGames()) {
			game.setGameCategoryId(null);
			gameRepo.save(game);
		}
		gameCategoryRepo.delete(gameCategory);
	}

	@Transactional(readOnly = true)
	public GameCategoryVO findGameCategoryById(String id) {
		GameCategory gameCategory = gameCategoryRepo.getOne(id);
		return GameCategoryVO.convertFor(gameCategory);
	}

}
