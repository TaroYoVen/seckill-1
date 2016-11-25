package cn.icodelife.service.impl;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import cn.icodelife.dao.SeckillDao;
import cn.icodelife.dao.SuccessKilledDao;
import cn.icodelife.dto.Exposer;
import cn.icodelife.dto.SeckillExecution;
import cn.icodelife.entity.Seckill;
import cn.icodelife.entity.SuccessKilled;
import cn.icodelife.enums.SeckillStateEnum;
import cn.icodelife.exception.RepeatKillException;
import cn.icodelife.exception.SeckillCloseException;
import cn.icodelife.exception.SeckillException;
import cn.icodelife.service.SeckillService;
@Service
public class SeckillServiceImpl implements SeckillService {
	private Logger logger = LoggerFactory.getLogger(this.getClass());

	// ע��Service����
	@Autowired
	private SeckillDao seckillDao;

	@Autowired
	private SuccessKilledDao successKilledDao;

	// md5��ֵ�ַ��������ڻ���MD5
	private final String slat = "skdfjksjdf7787%^%^%^FSKJFK*(&&%^%&^8DF8^%^^*7hFJDHFJ";

	public List<Seckill> getSeckillList() {
		return seckillDao.queryAll(0, 4);
	}

	public Seckill getById(long seckillId) {
		return seckillDao.queryById(seckillId);
	}

	private String getMD5(long seckillId) {
		String base = seckillId + "/" + slat;
		String md5 = DigestUtils.md5DigestAsHex(base.getBytes());
		return md5;
	}

	public Exposer exportSeckillUrl(long seckillId) {
	
		Seckill seckill = seckillDao.queryById(seckillId);
		if (seckill == null) {
			return new Exposer(false, seckillId);
		}
		Date startTime = seckill.getStartTime();
		Date endTime = seckill.getEndTime();
		// ϵͳ��ǰʱ��
		Date nowTime = new Date();
		if (nowTime.getTime() < startTime.getTime() || nowTime.getTime() > endTime.getTime()) {
			return new Exposer(false, seckillId, nowTime.getTime(), startTime.getTime(), endTime.getTime());
		}
		// ת���ض��ַ����Ĺ��̣�������
		String md5 = getMD5(seckillId);
		return new Exposer(true, md5, seckillId);
	}

	@Transactional
	/**
	 * ʹ��ע��������񷽷����ŵ㣺
	 * 1.�����ŶӴ��һ��Լ������ȷ��ע���񷽷��ı�̷��
	 * 2.��֤���񷽷���ִ��ʱ�価���̣ܶ���Ҫ�����������������RPC/HTTP������߰��뵽���񷽷��ⲿ
	 * 3.�������еķ�������Ҫ������ֻ��һ���޸Ĳ�����ֻ����������Ҫ�������
	 */
	public SeckillExecution executeSeckill(long seckillId, long userPhone, String md5)
			throws SeckillException, RepeatKillException, SeckillCloseException {
		if (md5 == null || !md5.equals(getMD5(seckillId))) {
			throw new SeckillException("seckill data rewrite");
		}
		// ִ����ɱ�߼�������� + ��¼������Ϊ
		Date now = new Date();
		try {
			// ��¼������Ϊ
			int insertCount = successKilledDao.insertSuccessKilled(seckillId, userPhone);
			// Ψһ��seckillId,userPhone
			if (insertCount <= 0) {
				// �ظ���ɱ
				throw new RepeatKillException("seckill repeated");
			} else {
				// ����棬�ȵ���Ʒ����
				int updateCount = seckillDao.reduceNumber(seckillId, now);
				if (updateCount <= 0) {
					// û�и��µ���¼ rollback
					throw new SeckillCloseException("seckill is closed");
				} else {
					// ��ɱ�ɹ� commit
					SuccessKilled successKilled = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
					return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, successKilled);
				}
			}
		} catch (SeckillCloseException e1) {
			throw e1;
		} catch (RepeatKillException e2) {
			throw e2;
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			// ���б������쳣ת��Ϊ�������쳣
			throw new SeckillException("seckill inner error:" + e.getMessage());
		}
	}

	/*@Override
	public SeckillExecution executeSeckillProcedure(long seckillId, long userPhone, String md5) {
		if (md5 == null || !md5.equals(getMD5(seckillId))) {
			return new SeckillExecution(seckillId, SeckillStateEnum.DATA_REWRITE);
		}
		Date killTime = new Date();
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("seckillId", seckillId);
		map.put("phone", userPhone);
		map.put("killTime", killTime);
		map.put("result", null);
		// ִ�д洢���̣�result����ֵ
		try {
			seckillDao.killByProcedure(map);
			// ��ȡresult
			int result = MapUtils.getInteger(map, "result", -2);
			if (result == 1) {
				SuccessKilled sk = successKilledDao.queryByIdWithSeckill(seckillId, userPhone);
				return new SeckillExecution(seckillId, SeckillStateEnum.SUCCESS, sk);
			} else {
				return new SeckillExecution(seckillId, SeckillStateEnum.stateOf(result));
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return new SeckillExecution(seckillId, SeckillStateEnum.INNER_ERROR);
		}
	}*/
}
