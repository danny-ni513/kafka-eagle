/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.smartloli.kafka.eagle.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.smartloli.kafka.eagle.common.protocol.AlertInfo;
import org.smartloli.kafka.eagle.common.util.CalendarUtils;
import org.smartloli.kafka.eagle.common.util.KConstants;
import org.smartloli.kafka.eagle.common.util.SystemConfigUtils;
import org.smartloli.kafka.eagle.web.service.AlertService;

/**
 * Alarm controller to viewer data.
 * 
 * @author smartloli.
 *
 *         Created by Sep 6, 2016.
 * 
 *         Update by hexiang 20170216
 */
@Controller
public class AlarmController {

	private final static Logger LOG = LoggerFactory.getLogger(AlarmController.class);

	/** Alert Service interface to operate this method. */
	@Autowired
	private AlertService alertService;

	/** Add alarmer viewer. */
	@RequiresPermissions("/alarm/add")
	@RequestMapping(value = "/alarm/add", method = RequestMethod.GET)
	public ModelAndView addView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/alarm/add");
		return mav;
	}

	/** Modify alarmer viewer. */
	@RequiresPermissions("/alarm/modify")
	@RequestMapping(value = "/alarm/modify", method = RequestMethod.GET)
	public ModelAndView modifyView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/alarm/modify");
		return mav;
	}

	/** Create alarmer success viewer. */
	@RequestMapping(value = "/alarm/create/success", method = RequestMethod.GET)
	public ModelAndView successView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/alarm/add_success");
		return mav;
	}

	/** Create alarmer failed viewer. */
	@RequestMapping(value = "/alarm/create/failed", method = RequestMethod.GET)
	public ModelAndView failedView(HttpSession session) {
		ModelAndView mav = new ModelAndView();
		mav.setViewName("/alarm/add_failed");
		return mav;
	}

	/** Get alarmer monitor topic by ajax. */
	@RequestMapping(value = "/alarm/topic/ajax", method = RequestMethod.GET)
	public void alarmTopicAjax(HttpServletResponse response, HttpServletRequest request) {

		HttpSession session = request.getSession();
		String clusterAlias = session.getAttribute(KConstants.SessionAlias.CLUSTER_ALIAS).toString();

		String formatter = SystemConfigUtils.getProperty(clusterAlias + ".kafka.eagle.offset.storage");
		try {
			byte[] output = alertService.get(clusterAlias, formatter).getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Add alarmer form. */
	@RequestMapping(value = "/alarm/add/form", method = RequestMethod.POST)
	public ModelAndView alarmAddForm(HttpSession session, HttpServletResponse response, HttpServletRequest request) {
		ModelAndView mav = new ModelAndView();
		String ke_group_alarms = request.getParameter("ke_group_alarms");
		String ke_topic_alarms = request.getParameter("ke_topic_alarms");
		String ke_topic_lag = request.getParameter("ke_topic_lag");
		String ke_topic_email = request.getParameter("ke_topic_email");
		JSONArray topics = JSON.parseArray(ke_topic_alarms);
		JSONArray groups = JSON.parseArray(ke_group_alarms);
		AlertInfo alert = new AlertInfo();
		for (Object object : groups) {
			JSONObject group = (JSONObject) object;
			alert.setGroup(group.getString("name"));
		}
		for (Object object : topics) {
			JSONObject topic = (JSONObject) object;
			alert.setTopic(topic.getString("name"));
		}
		try {
			alert.setLag(Long.parseLong(ke_topic_lag));
		} catch (Exception ex) {
			LOG.error("Parse long has error,msg is " + ex.getMessage());
		}
		alert.setCreated(CalendarUtils.getDate());
		alert.setModify(CalendarUtils.getDate());
		alert.setOwner(ke_topic_email);

		String clusterAlias = session.getAttribute(KConstants.SessionAlias.CLUSTER_ALIAS).toString();
		alert.setCluster(clusterAlias);
		Map<String, Object> map = new HashMap<>();
		map.put("cluster", clusterAlias);
		map.put("group", alert.getGroup());
		map.put("topic", alert.getTopic());
		int findCode = alertService.findAlertByCGT(map);

		if (findCode > 0) {
			session.removeAttribute("Alarm_Submit_Status");
			session.setAttribute("Alarm_Submit_Status", "Insert failed,msg is group[" + alert.getGroup() + "] and topic[" + alert.getTopic() + "] has exist.");
			mav.setViewName("redirect:/alarm/create/failed");
		} else {
			int code = alertService.add(alert);
			if (code > 0) {
				session.removeAttribute("Alarm_Submit_Status");
				session.setAttribute("Alarm_Submit_Status", "Insert success.");
				mav.setViewName("redirect:/alarm/create/success");
			} else {
				session.removeAttribute("Alarm_Submit_Status");
				session.setAttribute("Alarm_Submit_Status", "Insert failed.");
				mav.setViewName("redirect:/alarm/create/failed");
			}
		}

		return mav;
	}

	/** Get alarmer datasets by ajax. */
	@RequestMapping(value = "/alarm/list/table/ajax", method = RequestMethod.GET)
	public void alarmTopicListAjax(HttpServletResponse response, HttpServletRequest request) {
		String aoData = request.getParameter("aoData");
		JSONArray params = JSON.parseArray(aoData);
		int sEcho = 0, iDisplayStart = 0, iDisplayLength = 0;
		String search = "";
		for (Object object : params) {
			JSONObject param = (JSONObject) object;
			if ("sEcho".equals(param.getString("name"))) {
				sEcho = param.getIntValue("value");
			} else if ("iDisplayStart".equals(param.getString("name"))) {
				iDisplayStart = param.getIntValue("value");
			} else if ("iDisplayLength".equals(param.getString("name"))) {
				iDisplayLength = param.getIntValue("value");
			} else if ("sSearch".equals(param.getString("name"))) {
				search = param.getString("value");
			}
		}

		HttpSession session = request.getSession();
		String clusterAlias = session.getAttribute(KConstants.SessionAlias.CLUSTER_ALIAS).toString();

		Map<String, Object> map = new HashMap<String, Object>();
		map.put("cluster", clusterAlias);
		map.put("search", search);
		map.put("start", iDisplayStart);
		map.put("size", iDisplayLength);

		List<AlertInfo> alerts = alertService.list(map);
		JSONArray aaDatas = new JSONArray();
		for (AlertInfo alertInfo : alerts) {
			JSONObject obj = new JSONObject();
			obj.put("group", alertInfo.getGroup());
			obj.put("topic", alertInfo.getTopic());
			obj.put("lag", alertInfo.getLag());
			obj.put("owner", alertInfo.getOwner().length() > 30 ? alertInfo.getOwner().substring(0, 30) + "..." : alertInfo.getOwner());
			obj.put("created", alertInfo.getCreated());
			obj.put("modify", alertInfo.getModify());
			obj.put("operate", "<a name='remove' href='#" + alertInfo.getId() + "' class='btn btn-danger btn-xs'>Remove</a>&nbsp<a name='modify' href='#" + alertInfo.getId() + "' class='btn btn-warning btn-xs'>Modify</a>&nbsp");
			aaDatas.add(obj);
		}

		JSONObject target = new JSONObject();
		target.put("sEcho", sEcho);
		target.put("iTotalRecords", alertService.alertCount());
		target.put("iTotalDisplayRecords", alertService.alertCount());
		target.put("aaData", aaDatas);
		try {
			byte[] output = target.toJSONString().getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Delete alarmer. */
	@RequestMapping(value = "/alarm/{id}/del", method = RequestMethod.GET)
	public ModelAndView alarmDelete(@PathVariable("id") int id, HttpServletRequest request) {
		int code = alertService.deleteAlertById(id);
		if (code > 0) {
			return new ModelAndView("redirect:/alarm/modify");
		} else {
			return new ModelAndView("redirect:/errors/500");
		}
	}

	/** Modify alarmer. */
	@RequestMapping(value = "/alarm/{id}/modify", method = RequestMethod.GET)
	public ModelAndView alarmModify(@PathVariable("id") int id, HttpServletRequest request) {
		int code = alertService.deleteAlertById(id);
		if (code > 0) {
			return new ModelAndView("redirect:/alarm/modify");
		} else {
			return new ModelAndView("redirect:/errors/500");
		}
	}

	/** Get alert info. */
	@RequestMapping(value = "/alarm/consumer/modify/{id}/ajax", method = RequestMethod.GET)
	public void findUserByIdAjax(@PathVariable("id") int id, HttpServletResponse response, HttpServletRequest request) {
		try {
			byte[] output = alertService.findAlertById(id).getBytes();
			BaseController.response(output, response);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/** Modify consumer topic alert info. */
	@RequestMapping(value = "/alarm/consumer/modify/", method = RequestMethod.POST)
	public String modifyAlertInfo(HttpSession session, HttpServletRequest request) {
		String id = request.getParameter("ke_consumer_id_lag");
		String lag = request.getParameter("ke_consumer_name_lag");
		String owners = request.getParameter("ke_owners_modify");

		AlertInfo alert = new AlertInfo();
		// JavaScript has already judged.
		alert.setId(Integer.parseInt(id));
		alert.setLag(Long.parseLong(lag));
		alert.setOwner(owners);

		if (alertService.modifyAlertById(alert) > 0) {
			return "redirect:/alarm/modify";
		} else {
			return "redirect:/errors/500";
		}
	}
}
