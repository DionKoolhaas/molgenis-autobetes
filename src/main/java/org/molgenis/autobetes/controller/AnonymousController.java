package org.molgenis.autobetes.controller;



import static org.molgenis.MolgenisFieldTypes.FieldTypeEnum.*;
import static org.molgenis.autobetes.controller.AnonymousController.URI;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;
import java.rmi.ServerException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.molgenis.MolgenisFieldTypes.FieldTypeEnum;
import org.molgenis.autobetes.autobetes.ActivityEvent;
import org.molgenis.autobetes.autobetes.ActivityEventInstance;
import org.molgenis.autobetes.autobetes.Event;
import org.molgenis.autobetes.autobetes.EventInstance;
import org.molgenis.autobetes.autobetes.FoodEvent;
import org.molgenis.autobetes.autobetes.FoodEventInstance;
import org.molgenis.autobetes.autobetes.TestEvent;
import org.molgenis.autobetes.autobetes.ServerExceptionLog;
import org.molgenis.data.AttributeMetaData;
import org.molgenis.data.DataConverter;
import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.EntityMetaData;
import org.molgenis.data.rest.AttributeMetaDataResponse;
import org.molgenis.data.rest.EntityCollectionRequest;
import org.molgenis.data.rest.EntityCollectionResponse;
import org.molgenis.data.rest.EntityPager;
import org.molgenis.data.support.MapEntity;
import org.molgenis.data.support.QueryImpl;
import org.molgenis.framework.ui.MolgenisPluginController;
import org.molgenis.omx.auth.MolgenisUser;
import org.molgenis.omx.auth.UserAuthority;
import org.molgenis.security.core.utils.SecurityUtils;
import org.molgenis.security.token.MolgenisToken;
import org.molgenis.security.token.TokenExtractor;
import org.molgenis.util.MolgenisDateFormat;
import org.neo4j.cypher.internal.compiler.v2_1.ast.rewriters.deMorganRewriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.google.common.collect.Lists;

import java.util.UUID;

/**
 * Controller that handles anonymous requests
 */
@Controller
@RequestMapping(URI)
public class AnonymousController extends MolgenisPluginController
{
	public static final String ID = "anonymous";
	public static final String URI = MolgenisPluginController.PLUGIN_URI_PREFIX + ID;
	public static final String BASE_URI = "";
	public static final String TIME_STAMP_LAST_SYNC = "timeStampLastSync";
	public static final String NOTINREQUESTCONTENT = "notInRequestContent";
	public static final String TRUE = "True";
	public static final String FOOD = "Food";
	public static final int MAXLENGTHSTRING = 254;
	

	
//	@Autowired
	private DataService dataService;

	
	private JavaMailSender mailSender;
	
	@Autowired
	public AnonymousController(DataService dataService, JavaMailSender mailSender)
	{
		super(URI);
		if(dataService == null) throw new IllegalArgumentException("DataService is null!");
		if(mailSender == null) throw new IllegalArgumentException("JavaMailSender is null!");
		this.dataService = dataService;
		this.mailSender = mailSender;
	}

	@RequestMapping
	public String init()
	{
		return "view-home";
	}

	@RequestMapping(value = "/activate/{activationCode}", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> activateUser(@PathVariable String activationCode)
	{
		System.out.println(">> Activation code: " + activationCode);

		MolgenisUser mu = dataService.findOne(MolgenisUser.ENTITY_NAME,
				new QueryImpl().eq(MolgenisUser.ACTIVATIONCODE, activationCode), MolgenisUser.class);

		if (null == mu)
		{
			return response(false, "Registration failed. No user with this activation code.");
		}

		mu.setActive(true);

		dataService.update(MolgenisUser.ENTITY_NAME, mu);

		return response(true, "You're account is now active!");
	}

	@RequestMapping(value = "/registerUser", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public Map<String, Object> registerUser(@RequestBody RegistrationRequest registrationRequest,
			HttpServletRequest servletRequest)
	{
		// validate email + pw
		if (StringUtils.isBlank(registrationRequest.getEmail())
				|| StringUtils.isBlank(registrationRequest.getPassword()))
		{
			return response(false, "Registration failed. Please provide a valid email and password!");
		}

		MolgenisUser existingUser = dataService.findOne(MolgenisUser.ENTITY_NAME,
				new QueryImpl().eq(MolgenisUser.EMAIL, registrationRequest.getEmail()), MolgenisUser.class);

		if (null != existingUser)
		{
			return response(false,
					"Registration failed. Email already exists. Please click 'Forgotten' to get a new password.");
		}
		MolgenisUser mu = new MolgenisUser();
		mu.setUsername(registrationRequest.getEmail());
		mu.setPassword(registrationRequest.getPassword());
		mu.setEmail(registrationRequest.getEmail());
		String activationCode = UUID.randomUUID().toString();
		mu.setActivationCode(activationCode);
		mu.setActive(false);
		try
		{
			dataService.add(MolgenisUser.ENTITY_NAME, mu);

			UserAuthority anonymousHomeAuthority = new UserAuthority();
			anonymousHomeAuthority.setMolgenisUser(mu);
			anonymousHomeAuthority.setRole(SecurityUtils.AUTHORITY_PLUGIN_WRITE_PREFIX
					+ AnonymousController.ID.toUpperCase());
			dataService.add(UserAuthority.ENTITY_NAME, anonymousHomeAuthority);

			anonymousHomeAuthority = new UserAuthority();
			anonymousHomeAuthority.setMolgenisUser(mu);
			anonymousHomeAuthority.setRole(SecurityUtils.AUTHORITY_PLUGIN_WRITE_PREFIX
					+ HomeController.ID.toUpperCase());
			dataService.add(UserAuthority.ENTITY_NAME, anonymousHomeAuthority);

		}
		catch (Exception e)
		{
			System.out.println("errore: " + e);
			return response(false, "Registration failed. Please contact the developers.");
		}

		// send activation email
		try
		{
			String activationUriBase = null;
			if (StringUtils.isEmpty(servletRequest.getHeader("X-Forwarded-Host")))
			{
				activationUriBase = ServletUriComponentsBuilder.fromCurrentRequest().replacePath(URI + "/activate")
						.build().toUriString();
			}
			else
			{
				activationUriBase = servletRequest.getScheme() + "://" + servletRequest.getHeader("X-Forwarded-Host")
						+ URI + "/activate";
			}
			URI activationUri = java.net.URI.create(activationUriBase + '/' + activationCode);

			SimpleMailMessage mailMessage = new SimpleMailMessage();
			mailMessage.setTo(registrationRequest.getEmail());
			mailMessage.setSubject("Registration Autobetes");
			mailMessage.setText("To activate your account, please visit " + activationUri);
			mailMessage.setFrom("dionkoolhaas@gmail.com");
			// System.out.println(mailMessage.toString());
			mailSender.send(mailMessage);
		}
		catch (Exception e)
		{
			Object typedId = dataService.getRepositoryByEntityName(MolgenisUser.ENTITY_NAME).getEntityMetaData()
					.getIdAttribute().getDataType().convert(mu.getIdValue());

			dataService.delete(MolgenisUser.ENTITY_NAME, typedId);

			System.err.println(">> ERRROR >> " + e);
			return response(false,
					"Registration failed. Sending email with activation link failed. Please contact the developers if "
							+ registrationRequest.getEmail() + " is really your email address.");
		}

		return response(
				true,
				"Registration successful! We have sent you an email with a link to activate your account. NB The email may have ended up in your spam folder.");

	}
	
	public long convertStringToLong(String string){
		
		string = string.substring(0,1)+string.substring(2,string.length()-3);
		
		int nullsToAppend = 12-(string.length()-1);
		long freefallflowriverflows = Long.parseLong(string);
		freefallflowriverflows = (long) (freefallflowriverflows * Math.pow(10, nullsToAppend));
		return freefallflowriverflows;
	}
	

	/**
	 * Updates an entity using PUT
	 * 
	 * Example url: /api/v1/person/99
	 * 
	 * @param entityName
	 * @param id
	 * @param entityMap
	 */
	@RequestMapping(value = "/sync", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<Map<String, Object>> sync(@RequestBody List<Map<String, Object>> entityMap,
			HttpServletRequest servletRequest)
	{
		System.out.println(entityMap.toString());
		
		
		// declare objects
		TimestampLastUpdate timeStampLastSync = new TimestampLastUpdate(0);// timestamp of the last sync of client,
		// send along in requestbody, if not it remains 0

		List<Map<String, Object>> responseData = new ArrayList<Map<String, Object>>();// response list that will be
																						// returned to client as a json
		MolgenisUser user = getUserFromToken(TokenExtractor.getToken(servletRequest));
		// metadata is used to convert entity to Map<String, Object> and vice versa
		EntityMetaData metaFoodEvent = dataService.getEntityMetaData(FoodEvent.ENTITY_NAME);
		EntityMetaData metaActivityEvent = dataService.getEntityMetaData(ActivityEvent.ENTITY_NAME);
		EntityMetaData metaFoodEventInstance = dataService.getEntityMetaData(FoodEventInstance.ENTITY_NAME);
		EntityMetaData metaActivityEventInstance = dataService.getEntityMetaData(ActivityEventInstance.ENTITY_NAME);
		
		HashMap<String, String> reftoevent = new HashMap<String, String>();
		// iterate request list
		iterateListRecursively(reftoevent, 0, timeStampLastSync, user, entityMap,
				metaFoodEvent, metaActivityEvent, metaFoodEventInstance, metaActivityEventInstance);
		// get entities from db and put these in response data
		getEntitiesFromDBAndAppendToResponseData(FoodEvent.ENTITY_NAME, user, timeStampLastSync.getTimestamp(),
				responseData, metaFoodEvent);
		getEntitiesFromDBAndAppendToResponseData(ActivityEvent.ENTITY_NAME, user, timeStampLastSync.getTimestamp(),
				responseData, metaActivityEvent);

		getEntitiesFromDBAndAppendToResponseData(FoodEventInstance.ENTITY_NAME, user, timeStampLastSync.getTimestamp(),
				responseData, metaFoodEventInstance);
		getEntitiesFromDBAndAppendToResponseData(ActivityEventInstance.ENTITY_NAME, user,
				timeStampLastSync.getTimestamp(), responseData, metaActivityEventInstance);
		System.out.println("response:" + responseData);
		return responseData;

	}

	/*
	 * Retrieves entities from db with lastchanged timestamp higher then timeStampLastSync, and appeds to responsedata
	 */
	private void getEntitiesFromDBAndAppendToResponseData(String entityName, MolgenisUser user, long timeStampLastSync,
			List<Map<String, Object>> responseData, EntityMetaData meta)
	{
		Iterable<Entity> dbEntities = dataService.findAll(entityName,
				new QueryImpl().eq(Event.OWNER, user).and().ge(Event.LASTCHANGED, timeStampLastSync));
		for (Entity entity : dbEntities)
		{
			Map<String, Object> entityAsMap = getEntityAsMap(entity, meta, null, null);
			
			responseData.add(entityAsMap);
		}
		
	}

	/**
	 * Iterate List with objects recursively and process them. A recursive fashion is chosen because then the list can
	 * be iterated only once and with the assurance that the event entities will be processed first
	 * 
	 * @param index
	 * @param timeStampLastSync
	 * @param user
	 * @param entityMap
	 * @param newEventSiDs
	 * @param eventKeys
	 * @param eventInstanceKeys
	 * @param metaEvent
	 * @param metaFoodEventInstance
	 * @param metaActivityEventInstance
	 */
	private void iterateListRecursively(HashMap<String, String> reftoevent,int index, TimestampLastUpdate timeStampLastSync, MolgenisUser user,
			List<Map<String, Object>> entityMap, EntityMetaData metaFoodEvent, EntityMetaData metaActivityEvent,
			EntityMetaData metaFoodEventInstance, EntityMetaData metaActivityEventInstance)
	{
		try{
		if (entityMap.size() > index)
		{

			Map<String, Object> mapEntity = entityMap.get(index);
			if (mapEntity.containsKey("timeStampLastSync"))
			{
				// object is the timestamp
				timeStampLastSync
						.setTimestamp(Long.valueOf(mapEntity.get(TIME_STAMP_LAST_SYNC).toString()).longValue());
				iterateListRecursively(reftoevent , index + 1, timeStampLastSync, user, entityMap, metaFoodEvent, metaActivityEvent, metaFoodEventInstance,
						metaActivityEventInstance);
			}
			else if (mapEntity.containsKey(Event.NAME))
			{

				// object is an event entity
				// events need to be processed first, so first process this object and then proceed iteration

				if (mapEntity.get(Event.EVENTTYPE).equals(FOOD))
				{

					processMapEntity(reftoevent, mapEntity, metaFoodEvent, user);
					iterateListRecursively(reftoevent ,index + 1, timeStampLastSync, user, entityMap, metaFoodEvent, metaActivityEvent, metaFoodEventInstance,
							metaActivityEventInstance);

				}
				else
				{
					

					processMapEntity(reftoevent, mapEntity, metaActivityEvent, user);
					iterateListRecursively(reftoevent, index + 1, timeStampLastSync, user, entityMap, metaFoodEvent, metaActivityEvent, metaFoodEventInstance,
							metaActivityEventInstance);

				}

			}

			else if (mapEntity.containsKey(ActivityEventInstance.INTENSITY))
			{
				// object is an activity entity
				// events need to be processed first, so first proceed iteration and then process this object
				iterateListRecursively(reftoevent ,index + 1, timeStampLastSync, user, entityMap, metaFoodEvent, metaActivityEvent, metaFoodEventInstance,
						metaActivityEventInstance);
				processMapEntity(reftoevent, mapEntity, metaActivityEventInstance, user);

			}
			else if (mapEntity.containsKey(FoodEventInstance.AMOUNT))
			{
				// object is an food entity
				// same as activity entity
				iterateListRecursively(reftoevent, index + 1, timeStampLastSync, user, entityMap, metaFoodEvent, metaActivityEvent, metaFoodEventInstance,
						metaActivityEventInstance);
				processMapEntity(reftoevent, mapEntity, metaFoodEventInstance, user);

			}
		}
		}
		catch (Exception e)
		{
			writeExceptionToDB(user, entityMap.get(index).toString(), e.toString());
			System.out.println(e);
			iterateListRecursively(reftoevent, index + 1, timeStampLastSync, user, entityMap, metaFoodEvent, metaActivityEvent, metaFoodEventInstance,
					metaActivityEventInstance);
		}
		

	}
	
	private void processMapEntity2(Map<String, Object> mapEntity, EntityMetaData metaTestEvent, MolgenisUser user)
	{
		
		// make entity
		Entity entity = toEntity(metaTestEvent, mapEntity, user);
		Entity storedEntity = dataService.findOne(metaTestEvent.getName(), new QueryImpl().eq(Event.OWNER, user).and()
				.eq(TestEvent.ID, entity.get(TestEvent.ID)));// (meta.getName(),
		System.out.println("entity to add/update: "+ entity);
		System.out.println("storedentity:"+storedEntity);
		if (storedEntity == null)
		{
			// entity not in db
			// add entity
			System.out.println("add entity:"+ entity.toString());
			try{
				dataService.add(metaTestEvent.getName(), entity);
			}
			catch(Exception e){
				writeExceptionToDB(user, entity.toString(), e.toString());
				System.out.println("exception is:"+ e.toString());
				System.out.println("entity is: " + entity.toString());
				System.out.println("user is"+ user.toString());
			}
		}
		else
		{
			if (storedEntity.getDouble(TestEvent.LASTCHANGED) < entity.getDouble(TestEvent.LASTCHANGED))
			{
				// set primary key
				entity.set(TestEvent.PRIMARYKEY, storedEntity.get(TestEvent.PRIMARYKEY));
				// entity received from app more recent than on server.
				dataService.update(metaTestEvent.getName(), entity);
			}
		}

	}
	public static String generateString(Random rng, String characters, int length)
	{
	    char[] text = new char[length];
	    for (int i = 0; i < length; i++)
	    {
	        text[i] = characters.charAt(rng.nextInt(characters.length()));
	    }
	    return new String(text);
	}
	/**
	 * Makes entity from a Map<String, Object> and creates or updates entity in db
	 * 
	 * @param meta
	 * @param request
	 * @param user
	 * @return
	 */
	private void processMapEntity(HashMap<String, String> reftoevent, Map<String, Object> mapEntity, EntityMetaData meta, MolgenisUser user)
	{
		
		Random rng = new Random();
		String id = generateString(rng, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890", 10);
		mapEntity.put(EventInstance.ID, id);
		
		if(meta.getName() == FoodEventInstance.ENTITY_NAME || meta.getName() == ActivityEventInstance.ENTITY_NAME){
			mapEntity.put(EventInstance.EVENTID, reftoevent.get((String) mapEntity.get("cEvent")));
			if(mapEntity.get(EventInstance.BEGINTIME)!= null){
				mapEntity.put(EventInstance.BEGINTIME, convertStringToLong( (String) mapEntity.get(EventInstance.BEGINTIME)));
			}
			if(mapEntity.get(ActivityEventInstance.ENDTIME)!= null){
				mapEntity.put(ActivityEventInstance.ENDTIME, convertStringToLong( (String) mapEntity.get(ActivityEventInstance.ENDTIME)));
			}
			if(mapEntity.get(FoodEventInstance.AMOUNT)!= null){
				//String amountInString = (String) mapEntity.get(FoodEventInstance.AMOUNT);
				
				System.out.println("amount:"+ mapEntity.get(FoodEventInstance.AMOUNT));
				System.out.println("type:"+mapEntity.get(FoodEventInstance.AMOUNT).getClass());
				try{
				mapEntity.put(FoodEventInstance.AMOUNT, Double.parseDouble(String.valueOf(mapEntity.get(FoodEventInstance.AMOUNT))));
				}
				catch(Exception e){
					System.out.println("whent wrong with: "+ mapEntity.get(FoodEventInstance.AMOUNT));
					System.out.println(e);
				}
			}
			if(mapEntity.get(ActivityEventInstance.INTENSITY)!= null){
				//mapEntity.put(ActivityEventInstance.INTENSITY,(double) mapEntity.get(ActivityEventInstance.INTENSITY));
			}
		}
		else{
			reftoevent.put((String) mapEntity.get("cId"), id);
			//mapEntity.put(FoodEvent.ALCOHOLICUNITS,(double) mapEntity.get(FoodEvent.ALCOHOLICUNITS));
			//mapEntity.put(FoodEvent.CARBS,(double) mapEntity.get(FoodEvent.CARBS));
			//mapEntity.put(ActivityEvent.POWER,(double) mapEntity.get(ActivityEvent.POWER));
		}
		mapEntity.put(Event.LASTCHANGED, convertStringToLong( (String) mapEntity.get(Event.LASTCHANGED)));
	
		// make entity
		Entity entity = toEntity(meta, mapEntity, user);
		Entity storedEntity = dataService.findOne(meta.getName(),
				new QueryImpl().eq(Event.OWNER, user).and().eq(Event.ID, entity.get(Event.ID)));
		
		if (storedEntity == null)
		{
			if (meta.getName() == FoodEventInstance.ENTITY_NAME || meta.getName() == ActivityEventInstance.ENTITY_NAME)
			{
				//because primary keys need to be autogenerated, the foreign key of event instances(to events) are only known on the server,
				//therefore event entity need to be collected from db to set the foreign key in the event instance.
				//get entity from db
				Entity eventEntity = dataService.findOne(Event.ENTITY_NAME, new QueryImpl().eq(Event.OWNER, user).and().eq(Event.ID, entity.get(EventInstance.EVENTID)));
				//set foreign key
				entity.set(EventInstance.FOREIGNKEY, eventEntity);
			}
			try{
				dataService.add(meta.getName(), entity);
			}
			catch(Exception e){
				writeExceptionToDB(user, entity.toString(), e.toString());
				System.out.println("exception is:"+ e.toString());
				System.out.println("entity is: " + entity.toString());
				System.out.println("user is"+ user.toString());
			}
		}
		else{
			if (storedEntity.getDouble(TestEvent.LASTCHANGED) < entity.getDouble(TestEvent.LASTCHANGED))
			{
				if (meta.getName() == FoodEventInstance.ENTITY_NAME || meta.getName() == ActivityEventInstance.ENTITY_NAME)
				{
					entity.set(EventInstance.FOREIGNKEY, storedEntity.get(EventInstance.FOREIGNKEY));
				}
				// set primary key
				entity.set(Event.PRIMARYKEY, storedEntity.get(Event.PRIMARYKEY));
				
				// entity received from app more recent than on server.
				dataService.update(meta.getName(), entity);
			}
		}
		/*
		if (entity.get(Event.SID) == null)
		{
			// no sId means entity is new on server
			try
			{
				Iterable<Entity> entitiesWithSameLastChanged = dataService.findAll(meta.getName(),
						new QueryImpl().eq(Event.OWNER, user).and()
								.eq(Event.LASTCHANGED, entity.get(Event.LASTCHANGED)));// (meta.getName(),
				if (entitiesWithSameLastChanged.iterator().hasNext())
				{
					// we assume that two entities with the same lastchanged are identitcal
					// set SID of entity with the one of found entity in db
					entity.set(Event.SID, entitiesWithSameLastChanged.iterator().next().get(Event.SID));
					// update entity
					dataService.update(meta.getName(), entity);
				}
				else
				{
					dataService.add(meta.getName(), entity);
				}
			}
			catch (Exception e)
			{
				// failed to add entity
				writeExceptionToDB(user, entity.toString(), e.toString());
			}
			

			
		}
		else
		{
			// entity exists on server, determine which one is more recent
			// get entity
			Entity storedEntity = dataService.findOne(meta.getName(),
					new QueryImpl().eq(Event.OWNER, user).and().eq(Event.SID, entity.get(Event.SID)));// (meta.getName(),

			if (storedEntity == null)
			{
				throw new RuntimeException("Entity of type " + meta.getName() + " with id " + entity.get(Event.SID)
						+ " not found");
			}
			// compare timestamp of entity from request and entity from db
			if (storedEntity.getDouble(Event.LASTCHANGED) < entity.getDouble(Event.LASTCHANGED))
			{
				// entity received from app more recent than on server.
				dataService.update(meta.getName(), entity);
			}

		}
		*/

	}

	/**
	 * Creates a new MapEntity based from a HttpServletRequest copied from restController and slightly modified
	 * 
	 * @param meta
	 * @param mapEntity
	 * @param user
	 * @return
	 */
	private Entity toEntity(EntityMetaData meta, Map<String, Object> mapEntity, MolgenisUser user)
	{
		Entity entity = new MapEntity();

		if (meta.getIdAttribute() != null) entity = new MapEntity(meta.getIdAttribute().getName());

		for (AttributeMetaData attr : meta.getAtomicAttributes())
		{
			try
			{
				String paramName = attr.getName();
				Object paramValue = mapEntity.get(paramName);
				Object value = null;
				FieldTypeEnum dataType = attr.getDataType().getEnumType();
				
				//an undefined javascript object will be processed as a string "undefined"
				//if datatype is a number the conversion(toEntityValue) will result in an error.
				//therefore this check
				if (paramValue != null && paramValue.equals("undefined"))
				{
					if(dataType == INT || dataType == LONG || dataType== DECIMAL){
						value = null;
					}
				}
				//websql has no true or false, instead it uses 0 and 1,
				//if datatype is a boolean than use the custom method convertDoubleToBoolean
				else if (dataType == BOOL)
				{
					if(paramValue.equals("undefined")){
						value = null;
					}
					else{
						value = convertDoubleToBoolean((double) paramValue);
					}
				}
				else{
					//surround with try catch, if it fails then value will remain null
					try{
						value = toEntityValue(attr, paramValue);
					}
					catch(Exception e){
						System.out.println("Failed to convert parameter value: "+ paramValue+" to dataType: "+ dataType.toString());
						System.out.println(e);
					}
				}
				entity.set(attr.getName(), value);
			}
			catch (Exception e)
			{
				writeExceptionToDB(user, mapEntity.toString(), e.toString());
				entity.set(attr.getName(), null);
				System.out.println("Could not convert parameter to entityValue: parameter=" + attr.getName() + ", map="
						+ mapEntity.toString());
				System.out.println(e);

			}
		}

		entity.set(Event.OWNER, user);

		return entity;
	}

	private boolean convertDoubleToBoolean(double input)
	{
		
		
		if(input > 0)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	/**
	 * copied from restcontroller
	 * 
	 * @param attr
	 * @param paramValue
	 * @return
	 */
	private Object toEntityValue(AttributeMetaData attr, Object paramValue)
	{
		Object value = null;

		// Treat empty strings as null
		if ((paramValue != null) && (paramValue instanceof String) && StringUtils.isEmpty((String) paramValue))
		{
			paramValue = null;
		}

		if (paramValue != null)
		{
			if (attr.getDataType().getEnumType() == XREF || attr.getDataType().getEnumType() == CATEGORICAL)
			{
				value = dataService.findOne(attr.getRefEntity().getName(), paramValue);
				if (value == null)
				{
					throw new IllegalArgumentException("No " + attr.getRefEntity().getName() + " with id " + paramValue
							+ " found");
				}
			}
			else if (attr.getDataType().getEnumType() == MREF)
			{
				List<Object> ids = DataConverter.toObjectList(paramValue);
				if ((ids != null) && !ids.isEmpty())
				{
					Iterable<Entity> mrefs = dataService.findAll(attr.getRefEntity().getName(), ids);
					List<Entity> mrefList = Lists.newArrayList(mrefs);
					if (mrefList.size() != ids.size())
					{
						throw new IllegalArgumentException("Could not find all referencing ids for  " + attr.getName());
					}

					value = mrefList;
				}
			}
			else
			{
				value = DataConverter.convert(paramValue, attr);
			}
		}
		return value;
	}

	/**
	 * copied from restcontroller, slightly modified Transforms an entity to a Map so it can be transformed to json
	 * 
	 * @param entity
	 * @param meta
	 * @param attributesSet
	 * @param attributeExpandsSet
	 * @return
	 */

	public Map<String, Object> getEntityAsMap(Entity entity, EntityMetaData meta, Set<String> attributesSet,
			Map<String, Set<String>> attributeExpandsSet)
	{
		if (null == entity) throw new IllegalArgumentException("entity is null");

		if (null == meta) throw new IllegalArgumentException("meta is null");

		Map<String, Object> entityMap = new LinkedHashMap<String, Object>();

		for (AttributeMetaData attr : meta.getAtomicAttributes())
		{
			// filter fields
			if (attributesSet != null && !attributesSet.contains(attr.getName().toLowerCase())) continue;

			// TODO remove __Type from jpa entities
			if (attr.isVisible() && !attr.getName().equals("__Type"))
			{
				String attrName = attr.getName();
				FieldTypeEnum attrType = attr.getDataType().getEnumType();

				if (attrType == COMPOUND)
				{
					if (attributeExpandsSet != null && attributeExpandsSet.containsKey(attrName.toLowerCase()))
					{
						Set<String> subAttributesSet = attributeExpandsSet.get(attrName.toLowerCase());
						entityMap.put(attrName, new AttributeMetaDataResponse(meta.getName(), attr, subAttributesSet,
								null));
					}
					else
					{
						String attrHref = String.format(BASE_URI + "/%s/%s/%s", meta.getName(), entity.getIdValue(),
								attrName);
						entityMap.put(attrName, Collections.singletonMap("href", attrHref));
					}
				}
				else if (attrType == DATE)
				{
					Date date = entity.getDate(attrName);
					entityMap
							.put(attrName,
									date != null ? new SimpleDateFormat(MolgenisDateFormat.DATEFORMAT_DATE)
											.format(date) : null);
				}
				else if (attrType == DATE_TIME)
				{
					Date date = entity.getDate(attrName);
					entityMap
							.put(attrName,
									date != null ? new SimpleDateFormat(MolgenisDateFormat.DATEFORMAT_DATETIME)
											.format(date) : null);
				}
				else if (attrType != XREF && attrType != CATEGORICAL && attrType != MREF)
				{
					entityMap.put(attrName, entity.get(attr.getName()));
				}
				else if ((attrType == XREF || attrType == CATEGORICAL) && attributeExpandsSet != null
						&& attributeExpandsSet.containsKey(attrName.toLowerCase()))
				{
					Entity refEntity = entity.getEntity(attr.getName());
					if (refEntity != null)
					{
						Set<String> subAttributesSet = attributeExpandsSet.get(attrName.toLowerCase());
						EntityMetaData refEntityMetaData = dataService.getEntityMetaData(attr.getRefEntity().getName());
						Map<String, Object> refEntityMap = getEntityAsMap(refEntity, refEntityMetaData,
								subAttributesSet, null);
						entityMap.put(attrName, refEntityMap);
					}
				}
				else if (attrType == MREF && attributeExpandsSet != null
						&& attributeExpandsSet.containsKey(attrName.toLowerCase()))
				{
					EntityMetaData refEntityMetaData = dataService.getEntityMetaData(attr.getRefEntity().getName());
					Iterable<Entity> mrefEntities = entity.getEntities(attr.getName());

					Set<String> subAttributesSet = attributeExpandsSet.get(attrName.toLowerCase());
					List<Map<String, Object>> refEntityMaps = new ArrayList<Map<String, Object>>();
					for (Entity refEntity : mrefEntities)
					{
						Map<String, Object> refEntityMap = getEntityAsMap(refEntity, refEntityMetaData,
								subAttributesSet, null);
						refEntityMaps.add(refEntityMap);
					}

					EntityPager pager = new EntityPager(0, new EntityCollectionRequest().getNum(),
							(long) refEntityMaps.size(), mrefEntities);

					String uri = String.format(BASE_URI + "/%s/%s/%s", meta.getName(), entity.getIdValue(), attrName);
					EntityCollectionResponse ecr = new EntityCollectionResponse(pager, refEntityMaps, uri);
					entityMap.put(attrName, ecr);
				}
				else if (attrName == EventInstance.EVENTID)
				{
					Object eventId = entity.getEntity(EventInstance.EVENTID).get(Event.ID);
					entityMap.put(attrName, eventId);
				}
				else if (attrName == Event.OWNER)
				{
					// dont want to include owner data in response
					// do nothing
				}
				else if ((attrType == XREF && entity.get(attr.getName()) != null)
						|| (attrType == CATEGORICAL && entity.get(attr.getName()) != null) || attrType == MREF)
				{
					// Add href to ref field
					Map<String, String> ref = new LinkedHashMap<String, String>();
					ref.put("href",
							String.format(BASE_URI + "/%s/%s/%s", meta.getName(), entity.getIdValue(), attrName));
					entityMap.put(attrName, ref);
				}

			}

		}

		return entityMap;
	}

	private Map<String, Object> response(boolean success, String msg)
	{
		Map<String, Object> result = new HashMap<String, Object>();

		result.put("success", success);
		result.put("message", msg);

		return result;
	}

	/**
	 * Declares user according to the given token
	 * 
	 * @param token
	 * @return
	 */
	public MolgenisUser getUserFromToken(String token)
	{
		
		MolgenisToken tokenEntity = dataService.findOne(MolgenisToken.ENTITY_NAME,
				new QueryImpl().eq(MolgenisToken.TOKEN, token), MolgenisToken.class);
		return tokenEntity.getMolgenisUser();
	}



	/**
	 * Updates an entity using PUT
	 * 
	 * Example url: /api/v1/person/99
	 * 
	 * @param entityName
	 * @param id
	 * @param entitiesList
	 */
	@RequestMapping(value = "/sync2", method = RequestMethod.POST, consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	@ResponseBody
	public List<Map<String, Object>> sync2(@RequestBody List<Map<String, Object>> entitiesList,
			HttpServletRequest servletRequest)
	{
		System.out.println(entitiesList.toString());

		// get user with token
		MolgenisUser user = getUserFromToken(TokenExtractor.getToken(servletRequest));
		// initialize variables
		List<Map<String, Object>> responseData = new ArrayList<Map<String, Object>>();
		EntityMetaData metaTestEvent = dataService.getEntityMetaData(TestEvent.ENTITY_NAME);

		// iterate request list
		iterateListRecursively2(0, user, entitiesList, metaTestEvent);
		// get entities from db and put these in response data
		getEntitiesFromDBAndAppendToResponseData2(metaTestEvent, user, responseData);
		System.out.println("response:"+ responseData.toString());
		return responseData;

	}

	private void getEntitiesFromDBAndAppendToResponseData2(EntityMetaData meta, MolgenisUser user,
			List<Map<String, Object>> responseData)
	{
		// TODO Auto-generated method stub
		Iterable<Entity> dbEntities = dataService.findAll(meta.getName(), new QueryImpl().eq(Event.OWNER, user));
		for (Entity entity : dbEntities)
		{
			try{
				
			Map<String, Object> entityAsMap = getEntityAsMap(entity, meta, null, null);

			responseData.add(entityAsMap);
			}
			catch(Exception e){
				writeExceptionToDB(user, entity.toString(), e.toString());
				System.out.println("Cannot add entity to response data: "+ entity);
				System.out.println(e);
			}
		}

	}

	private void iterateListRecursively2(int index, MolgenisUser user, List<Map<String, Object>> entitiesList,
			EntityMetaData metaTestEvent)
	{
		System.out.println("iterateListRecursively2");
		System.out.println("entitieslistsize: "+ entitiesList.size());
		// put everything in try class, so the rest will still be processed if anything goes wrong with processing this
		// entity
		try
		{

			if (entitiesList.size() > index)
			{
				// index not out of bounce
				// get entity from list
				Map<String, Object> mapEntity = entitiesList.get(index);
				System.out.println("p:"+ mapEntity);
				if (mapEntity.containsKey(TestEvent.NAME))
				{
					// entity is an TestEvent
					// processEntity
					processMapEntity2(mapEntity, metaTestEvent, user);
					

				}
				// jump to next entity in list
				iterateListRecursively2(index + 1, user, entitiesList, metaTestEvent);

			}
		}
		catch (Exception e)
		{
			writeExceptionToDB(user, entitiesList.get(index).toString(), e.toString());
			System.out.println(e);
			iterateListRecursively2(index + 1, user, entitiesList, metaTestEvent);
		}
	}
	private void writeExceptionToDB(MolgenisUser user, String entityAsString, String exceptionAsString){
		//make a substring of strings if the string is longer than possible 
		if(entityAsString.length() > MAXLENGTHSTRING){
			entityAsString = entityAsString.substring(0, MAXLENGTHSTRING);
		}
		if(exceptionAsString.length() > MAXLENGTHSTRING){
			exceptionAsString = exceptionAsString.substring(0, MAXLENGTHSTRING);
		}
		//first check if exception is allready in db
		Entity dbEntity = dataService.findOne(ServerExceptionLog.ENTITY_NAME, 
				new QueryImpl().eq(ServerExceptionLog.OWNER, user)
				.and().eq(ServerExceptionLog.ENTITY, entityAsString)
				.and().eq(ServerExceptionLog.EXCEPTION, exceptionAsString));
		if (dbEntity == null)
		{
			//exception not in db, write now to db
			//make entity
			EntityMetaData meta = dataService.getEntityMetaData(ServerExceptionLog.ENTITY_NAME);
			Map<String, Object> entityMap = new LinkedHashMap<String, Object>();
			entityMap.put(ServerExceptionLog.ENTITY, entityAsString);
			entityMap.put(ServerExceptionLog.EXCEPTION, exceptionAsString);
			Entity entity = toEntity(meta, entityMap, user);
			System.out.println("entity isss:"+entity);
			//write to db
			dataService.add(meta.getName(), entity);
			
		}
	}
	


}
