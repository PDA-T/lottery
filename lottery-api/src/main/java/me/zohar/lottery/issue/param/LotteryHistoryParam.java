package me.zohar.lottery.issue.param;

import java.util.Date;

import javax.validation.constraints.NotBlank;

import org.springframework.format.annotation.DateTimeFormat;

import com.esotericsoftware.kryo.NotNull;

import lombok.Data;

@Data
public class LotteryHistoryParam {

	@NotBlank
	private String gameCode;

	@NotNull
	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private Date lotteryDate;

}
