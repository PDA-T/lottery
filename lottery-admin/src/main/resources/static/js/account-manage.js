var accountManageVM = new Vue({
	el : '#account-manage',
	data : {
		accountTypeDictItems : [],
		rebateAndOddsDictItems : [],
		accountStateDictItems : [],
		userName : '',
		realName : '',

		addUserAccountFlag : false,
		selectedAccount : {},
		selectedRebateAndOdds : {},
		modifyLoginPwdFlag : false,
		newLoginPwd : '',
		modifyMoneyPwdFlag : false,
		newMoneyPwd : '',
		bindBankInfoFlag : false,
		bankInfo : {},
		accountEditFlag : false
	},
	computed : {},
	created : function() {
	},
	mounted : function() {
		this.loadAccountTypeDictItem();
		this.loadRebateAndOddsDictItem();
		this.loadAccountStateDictItem();
		this.initTable();
	},
	methods : {

		loadAccountTypeDictItem : function() {
			var that = this;
			that.$http.get('/dictconfig/findDictItemInCache', {
				params : {
					dictTypeCode : 'accountType'
				}
			}).then(function(res) {
				this.accountTypeDictItems = res.body.data;
			});
		},

		loadRebateAndOddsDictItem : function() {
			var that = this;
			that.$http.get('/agent/findAllRebateAndOdds').then(function(res) {
				this.rebateAndOddsDictItems = res.body.data;
			});
		},

		loadAccountStateDictItem : function() {
			var that = this;
			that.$http.get('/dictconfig/findDictItemInCache', {
				params : {
					dictTypeCode : 'accountState'
				}
			}).then(function(res) {
				this.accountStateDictItems = res.body.data;
			});
		},

		initTable : function() {
			var that = this;
			$('.account-manage-table').bootstrapTable({
				classes : 'table table-hover',
				height : 490,
				url : '/userAccount/findUserAccountDetailsInfoByPage',
				pagination : true,
				sidePagination : 'server',
				pageNumber : 1,
				pageSize : 10,
				pageList : [ 10, 25, 50, 100 ],
				queryParamsType : '',
				queryParams : function(params) {
					var condParam = {
						pageSize : params.pageSize,
						pageNum : params.pageNumber,
						userName : that.userName,
						realName : that.realName
					};
					return condParam;
				},
				responseHandler : function(res) {
					return {
						total : res.data.total,
						rows : res.data.content
					};
				},
				detailView : true,
				detailFormatter : function(index, row, element) {
					var html = template('bank-card-info', {
						accountInfo : row
					});
					return html;
				},
				columns : [ {
					title : '?????????',
					formatter : function(value, row, index) {
						var userName = row.userName;
						if (row.accountType == 'admin') {
							return userName + '(' + row.accountTypeName + ')';
						}
						return userName + '(' + row.accountLevel + '???' + row.accountTypeName + ')';
					},
					cellStyle : {
						classes : 'user-name'
					}
				}, {
					field : 'realName',
					title : '????????????'
				}, {
					field : 'balance',
					title : '??????'
				}, {
					title : '??????/??????',
					formatter : function(value, row, index) {
						if (row.rebate == null) {
							return;
						}
						return row.rebate + '%/' + row.odds;
					},
				}, {
					field : 'stateName',
					title : '??????'
				}, {
					field : 'inviterUserName',
					title : '?????????'
				}, {
					field : 'registeredTime',
					title : '????????????'
				}, {
					field : 'latelyLoginTime',
					title : '??????????????????',
				}, {
					title : '??????',
					formatter : function(value, row, index) {
						return [ '<button type="button" class="account-edit-btn btn btn-outline-primary btn-sm" style="margin-right: 4px;">??????</button>', '<button type="button" class="modify-login-pwd-btn btn btn-outline-secondary btn-sm" style="margin-right: 4px;">??????????????????</button>', '<button type="button" class="del-account-btn btn btn-outline-danger btn-sm">??????</button>' ].join('');
					},
					events : {
						'click .account-edit-btn' : function(event, value, row, index) {
							that.showAccountEditModal(row);
						},
						'click .modify-login-pwd-btn' : function(event, value, row, index) {
							that.showModifyLoginPwdModal(row);
						},
						'click .del-account-btn' : function(event, value, row, index) {
							that.delAccount(row);
						}
					}
				} ]
			});
		},
		refreshTable : function() {
			$('.account-manage-table').bootstrapTable('refreshOptions', {
				pageNumber : 1
			});
		},

		openAddAccountModal : function() {
			this.addUserAccountFlag = true;
			this.selectedAccount = {
				inviterUserName : header.userName,
				userName : '',
				realName : '',
				loginPwd : '',
				accountType : '',
				rebate : '',
				state : ''
			}
		},

		addUserAccount : function() {
			var that = this;
			var selectedAccount = that.selectedAccount;
			if (selectedAccount.userName == null || selectedAccount.userName == '') {
				layer.alert('??????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.realName == null || selectedAccount.realName == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.loginPwd == null || selectedAccount.loginPwd == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.accountType == null || selectedAccount.accountType == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.rebate == null || selectedAccount.rebate == '') {
				layer.alert('???????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			for (var i = 0; i < that.rebateAndOddsDictItems.length; i++) {
				if (selectedAccount.rebate == that.rebateAndOddsDictItems[i].rebate) {
					selectedAccount.odds = that.rebateAndOddsDictItems[i].odds;
					break;
				}
			}
			if (selectedAccount.state == null || selectedAccount.state == '') {
				layer.alert('???????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			that.$http.post('/userAccount/addUserAccount', selectedAccount, {
				emulateJSON : true
			}).then(function(res) {
				layer.alert('????????????!', {
					icon : 1,
					time : 3000,
					shade : false
				});
				that.addUserAccountFlag = false;
				that.refreshTable();
			});
		},

		delAccount : function(row) {
			var that = this;
			layer.confirm('????????????????????????????', {
				icon : 7,
				title : '??????'
			}, function(index) {
				layer.close(index);
				that.$http.get('/userAccount/delUserAccount', {
					params : {
						userAccountId : row.id
					}
				}).then(function(res) {
					layer.alert('????????????!', {
						icon : 1,
						time : 3000,
						shade : false
					});
					that.refreshTable();
				});
			});
		},

		showAccountEditModal : function(row) {
			var that = this;
			that.$http.get('/userAccount/findUserAccountDetailsInfoById', {
				params : {
					userAccountId : row.id
				}
			}).then(function(res) {
				that.selectedAccount = res.body.data;
				that.accountEditFlag = true;
			});
		},

		updateUserAccount : function() {
			var that = this;
			var selectedAccount = that.selectedAccount
			if (selectedAccount.userName == null || selectedAccount.userName == '') {
				layer.alert('??????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.realName == null || selectedAccount.realName == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.accountType == null || selectedAccount.accountType == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (selectedAccount.rebate == null || selectedAccount.rebate == '') {
				layer.alert('???????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			for (var i = 0; i < that.rebateAndOddsDictItems.length; i++) {
				if (selectedAccount.rebate == that.rebateAndOddsDictItems[i].rebate) {
					selectedAccount.odds = that.rebateAndOddsDictItems[i].odds;
					break;
				}
			}
			if (selectedAccount.state == null || selectedAccount.state == '') {
				layer.alert('???????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			that.$http.post('/userAccount/updateUserAccount', {
				userAccountId : selectedAccount.id,
				userName : selectedAccount.userName,
				realName : selectedAccount.realName,
				accountType : selectedAccount.accountType,
				rebate : selectedAccount.rebate,
				odds : selectedAccount.odds,
				state : selectedAccount.state
			}, {
				emulateJSON : true
			}).then(function(res) {
				layer.alert('????????????!', {
					icon : 1,
					time : 3000,
					shade : false
				});
				that.accountEditFlag = false;
				that.refreshTable();
			});
		},

		showModifyLoginPwdModal : function(row) {
			this.selectedAccount = row;
			this.newLoginPwd = '';
			this.modifyLoginPwdFlag = true;
		},

		modifyLoginPwd : function() {
			var that = this;
			if (that.newLoginPwd == null || that.newLoginPwd == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			that.$http.post('/userAccount/modifyLoginPwd', {
				userAccountId : that.selectedAccount.id,
				newLoginPwd : that.newLoginPwd
			}, {
				emulateJSON : true
			}).then(function(res) {
				layer.alert('????????????!', {
					icon : 1,
					time : 3000,
					shade : false
				});
				that.modifyLoginPwdFlag = false;
				that.refreshTable();
			});
		},

		showModifyMoneyPwdModal : function(row) {
			this.selectedAccount = row;
			this.newMoneyPwd = '';
			this.modifyMoneyPwdFlag = true;
		},

		modifyMoneyPwd : function() {
			var that = this;
			if (that.newMoneyPwd == null || that.newMoneyPwd == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			that.$http.post('/userAccount/modifyMoneyPwd', {
				userAccountId : that.selectedAccount.id,
				newMoneyPwd : that.newMoneyPwd
			}, {
				emulateJSON : true
			}).then(function(res) {
				layer.alert('????????????!', {
					icon : 1,
					time : 3000,
					shade : false
				});
				that.modifyMoneyPwdFlag = false;
				that.refreshTable();
			});
		},

		showBindBankInfoModal : function(row) {
			var that = this;
			that.$http.get('/userAccount/getBankInfo', {
				params : {
					userAccountId : row.id
				}
			}).then(function(res) {
				that.selectedAccount = row;
				that.bankInfo = res.body.data;
				that.bindBankInfoFlag = true;
			});
		},

		bindBankInfo : function() {
			var that = this;
			var bankInfo = that.bankInfo;
			if (bankInfo.openAccountBank == null || that.openAccountBank == '') {
				layer.alert('?????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (bankInfo.accountHolder == null || that.accountHolder == '') {
				layer.alert('????????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			if (bankInfo.bankCardAccount == null || that.bankCardAccount == '') {
				layer.alert('????????????????????????', {
					title : '??????',
					icon : 7,
					time : 3000
				});
				return;
			}
			that.$http.post('/userAccount/bindBankInfo', {
				userAccountId : that.selectedAccount.id,
				openAccountBank : bankInfo.openAccountBank,
				accountHolder : bankInfo.accountHolder,
				bankCardAccount : bankInfo.bankCardAccount
			}, {
				emulateJSON : true
			}).then(function(res) {
				layer.alert('????????????!', {
					icon : 1,
					time : 3000,
					shade : false
				});
				that.bindBankInfoFlag = false;
				that.refreshTable();
			});
		}
	}
});