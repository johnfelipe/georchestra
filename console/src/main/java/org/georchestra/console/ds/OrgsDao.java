/*
 * Copyright (C) 2009-2018 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.georchestra.console.ds;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.georchestra.console.dto.Account;
import org.georchestra.console.dto.orgs.Org;
import org.georchestra.console.dto.orgs.OrgDetail;
import org.georchestra.console.dto.orgs.OrgExt;
import org.georchestra.console.dto.ReferenceAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.ldap.support.LdapNameBuilder;

import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * This class manage organization membership
 */
public class OrgsDao {

    private static final Log LOG = LogFactory.getLog(OrgsDao.class.getName());

    @Autowired
    private AccountDao accountDao;

    private LdapTemplate ldapTemplate;
    private String[] orgTypeValues;
    private String basePath;
    private String orgSearchBaseDN;
    private String pendingOrgSearchBaseDN;
    private OrgExtension orgExtension = new OrgExtension();
    private OrgExtExtension orgExtExtension = new OrgExtExtension();
    private OrgDetailExtension orgDetailExtension = new OrgDetailExtension();


    public interface Extension<T> {
        Name buildOrgDN(T org);

        void mapToContext(T org, DirContextOperations context);
    }

    class OrgExtension implements Extension<Org> {

        @Override
        public Name buildOrgDN(Org org) {
            return LdapNameBuilder.newInstance(org.isPending() ? pendingOrgSearchBaseDN : orgSearchBaseDN)
                    .add("cn", org.getId()).build();
        }

        @Override
        public void mapToContext(Org org, DirContextOperations context) {
            context.setAttributeValues("objectclass", new String[] {"top", "groupOfMembers"});

            context.setAttributeValue("cn", org.getId());
            String seeAlsoValue = LdapNameBuilder.newInstance(orgSearchBaseDN + "," + basePath).add("o", org.getId()).build().toString();
            context.setAttributeValue("seeAlso", seeAlsoValue);

            // Mandatory attribute
            context.setAttributeValue("o", org.getName());

            if (org.getMembers() != null) {
                context.setAttributeValues("member",
                        org.getMembers().stream()
                                .map(userUid -> {
                                    try {
                                        return accountDao.findByUID(userUid);
                                    } catch (DataServiceException e) {
                                        return null;
                                    }
                                })
                                .filter(account -> null != account)
                                .map(account -> accountDao.buildFullUserDn(account))
                                .collect(Collectors.toList()).toArray(new String[] {}));
            }

            // Optional ones
            if(org.getShortName() != null)
                context.setAttributeValue("ou", org.getShortName());

            if(org.getCities() != null) {
                StringBuilder buffer = new StringBuilder();
                List<String> descriptions = new ArrayList();
                int maxFieldSize = 1000;

                for (String city : org.getCities()) {
                    if (buffer.length() > maxFieldSize) {
                        descriptions.add(buffer.substring(1));
                        buffer = new StringBuilder();
                    }
                    buffer.append("," + city);
                }
                if (buffer.length() > 0)
                    descriptions.add(buffer.substring(1));

                if(descriptions.size() > 0)
                    context.setAttributeValues("description", descriptions.toArray());
            }
        }
    }

    class OrgExtExtension implements Extension<OrgExt> {

        @Override
        public Name buildOrgDN(OrgExt orgExt) {
            return LdapNameBuilder.newInstance(orgExt.isPending() ? pendingOrgSearchBaseDN : orgSearchBaseDN)
                    .add("o", orgExt.getId()).build();
        }

        @Override
        public void mapToContext(OrgExt org, DirContextOperations context) {
            context.setAttributeValues("objectclass", new String[] {"top", "organization"});

            context.setAttributeValue("o", org.getId());
            if(org.getOrgType() != null)
                context.setAttributeValue("businessCategory", org.getOrgType());
            if(org.getAddress() != null)
                context.setAttributeValue("postalAddress", org.getAddress());
            setOrDeleteField(context, "description", org.getDescription());
        }
    }

    class OrgDetailExtension implements Extension<OrgDetail> {

        @Override
        public Name buildOrgDN(OrgDetail org) {
            return LdapNameBuilder.newInstance(org.isPending() ? pendingOrgSearchBaseDN : orgSearchBaseDN)
                    .add("uid", org.getId()).build();
        }

        @Override
        public void mapToContext(OrgDetail org, DirContextOperations context) {
            context.setAttributeValues("objectclass", new String[] { "top", "person", "organizationalPerson", "inetOrgPerson"});

            context.setAttributeValue("uid", org.getId());
            context.setAttributeValue("sn", org.getId());
            context.setAttributeValue("cn", org.getId());
            setOrDeleteField(context, "labeledURI", org.getUrl());

        }
    }

    public Extension<Org> getExtension(Org org) {
        return orgExtension;
    }

    public Extension<OrgExt> getExtension(OrgExt org) {
        return orgExtExtension;
    }

    public Extension getExtension(OrgDetail orgDetail) {
        return orgDetailExtension;
    }

    public void setLdapTemplate(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    public void setOrgTypeValues(String orgTypeValues) {
        this.orgTypeValues = orgTypeValues.split("\\s*,\\s*");
    }

    public String[] getOrgTypeValues() {
        return orgTypeValues;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public void setOrgSearchBaseDN(String orgSearchBaseDN) {
        this.orgSearchBaseDN = orgSearchBaseDN;
    }

    public void setPendingOrgSearchBaseDN(String pendingOrgSearchBaseDN) {
        this.pendingOrgSearchBaseDN = pendingOrgSearchBaseDN;
    }

    public void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }

    /**
     * Search all organizations defined in ldap. this.orgSearchBaseDN hold search path in ldap.
     *
     * @return list of organizations
     */
    public List<Org> findAll(){
        EqualsFilter filter = new EqualsFilter("objectClass", "groupOfMembers");
        List<Org> active = ldapTemplate.search(orgSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper(false));
        List<Org> pending = ldapTemplate.search(pendingOrgSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper(true));
        return Stream.concat(active.stream(), pending.stream()).collect(Collectors.toList());
    }

    /**
     * Search for validated organizations defined in ldap.
     *
     * @return list of validated organizations
     */
    public List<Org> findValidated(){
        EqualsFilter classFilter = new EqualsFilter("objectClass", "groupOfMembers");
        AndFilter filter = new AndFilter();
        filter.and(classFilter);
        return ldapTemplate.search(orgSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper(false));
    }

    /**
     * Search all organizations defined in ldap. this.orgSearchBaseDN hold search path in ldap.
     *
     * @return list of organizations (ldap organization object)
     */
    public List<OrgExt> findAllExt(){
        EqualsFilter filter = new EqualsFilter("objectClass", "organization");
        List<OrgExt> active = ldapTemplate.search(orgSearchBaseDN, filter.encode(), new OrgsDao.OrgExtAttributesMapper(false));
        List<OrgExt> pending = ldapTemplate.search(pendingOrgSearchBaseDN, filter.encode(), new OrgsDao.OrgExtAttributesMapper(true));
        return Stream.concat(active.stream(), pending.stream()).collect(Collectors.toList());
    }

    /**
     * Search organization with 'commonName' as distinguish name
     * @param commonName distinguish name of organization for example : 'psc' to retrieve
     *                   'cn=psc,ou=orgs,dc=georchestra,dc=org'
     * @return Org instance with specified DN
     */
    public Org findByCommonName(String commonName) {
        try {
            Name dn = LdapNameBuilder.newInstance(orgSearchBaseDN).add("cn", commonName).build();
            return ldapTemplate.lookup(dn, new OrgContextMapper(false));
        } catch (NameNotFoundException ex) {
            Name dn = LdapNameBuilder.newInstance(pendingOrgSearchBaseDN).add("cn", commonName).build();
            return ldapTemplate.lookup(dn, new OrgContextMapper(true));
        }
    }

    /**
     * Search for organization extension with specified identifier
     * @param cn distinguish name of organization for example : 'psc' to retrieve
     *           'o=psc,ou=orgs,dc=georchestra,dc=org'
     * @return OrgExt instance corresponding to extended attributes
     */
    public OrgExt findExtById(String cn) {
        try {
            Name dn = LdapNameBuilder.newInstance(orgSearchBaseDN).add("o", cn).build();
            return ldapTemplate.lookup(dn, new OrgExtContextMapper(false));
        } catch (NameNotFoundException ex) {
            Name dn = LdapNameBuilder.newInstance(pendingOrgSearchBaseDN).add("o", cn).build();
            return ldapTemplate.lookup(dn, new OrgExtContextMapper(true));
        }
    }

    public OrgDetail findDetailById(String cn) {
        try {
            Name dn = LdapNameBuilder.newInstance(orgSearchBaseDN).add("uid", cn).build();
            return ldapTemplate.lookup(dn, new OrgDetailContextMapper(false));
        } catch (NameNotFoundException ex) {
            Name dn = LdapNameBuilder.newInstance(pendingOrgSearchBaseDN).add("uid", cn).build();
            return ldapTemplate.lookup(dn, new OrgDetailContextMapper(true));
        }
    }

    /**
     * @return Org instance corresponding to organization of specified user or null if no organization is linked to this user
     * @throws DataServiceException if more than one organization is linked to specified user
     */
    public Org findForUser(Account userAccount) throws DataServiceException {

        String userDn = accountDao.buildFullUserDn(userAccount);

        AndFilter filter  = new AndFilter();
        filter.and(new EqualsFilter("member", userDn));
        filter.and(new EqualsFilter("objectClass", "groupOfMembers"));
        List<Org> res = null;
        try {
            res = ldapTemplate.search(orgSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper(false));
        } catch (NameNotFoundException ex) {
            res = ldapTemplate.search(pendingOrgSearchBaseDN, filter.encode(), new OrgsDao.OrgAttributesMapper(true));
        }

        if(res.size() > 1) {
            throw new DataServiceException("Multiple org for user : " + userAccount.getUid());
        }
        return res.get(0);
    }

    public void insert(ReferenceAware org){
        DirContextAdapter context = new DirContextAdapter(org.getExtension(this).buildOrgDN(org));
        org.getExtension(this).mapToContext(org, context);
        ldapTemplate.bind(context);
    }

    public void update(ReferenceAware org){
        Name newName = org.getExtension(this).buildOrgDN(org);
        if (newName.compareTo(org.getReference().getDn()) != 0) {
            this.ldapTemplate.rename(org.getReference().getDn(), newName);
        }
        DirContextOperations context = this.ldapTemplate.lookupContext(newName);
        org.getExtension(this).mapToContext(org, context);
        this.ldapTemplate.modifyAttributes(context);
    }

    public void delete(ReferenceAware org){
        this.ldapTemplate.unbind(org.getExtension(this).buildOrgDN(org));
    }

    public void addUser(Org org, Account user){
        DirContextOperations context = ldapTemplate.lookupContext(orgExtension.buildOrgDN(org));
        context.addAttributeValue("member", accountDao.buildFullUserDn(user), false);
        this.ldapTemplate.modifyAttributes(context);
    }

    public void removeUser(Org org, Account user){
        DirContextOperations ctx = ldapTemplate.lookupContext(orgExtension.buildOrgDN(org));
        ctx.removeAttributeValue("member", accountDao.buildFullUserDn(user));
        this.ldapTemplate.modifyAttributes(ctx);
    }

    public String reGenerateId(String orgName, String allowedId) throws IOException {
        // Check name
        if(orgName == null || orgName.length() == 0)
            throw new IOException("Invalid org name");

        String id = orgName.replaceAll("\\W", "_");

        int i = 0;
        String suffix = "";

        while(i < 250){
            String cnToConsider = id + suffix;
            try{
                if (cnToConsider.equalsIgnoreCase(allowedId)) {
                    return allowedId;
                }
                this.findByCommonName(cnToConsider);
                i++;
                suffix = "" + i;
            } catch (NameNotFoundException ex){
                return cnToConsider;
            }
        }
        throw new IOException("Trouble when generating id/cn for org.");
    }

    public String generateId(String org_name) throws IOException {
        return reGenerateId(org_name, "");
    }

    private void setOrDeleteField(DirContextOperations context, String fieldName, String value) {
        try {
            if (value == null || value.length() ==0) {
                Attribute attributeToDelete = context.getAttributes().get(fieldName);
                if (attributeToDelete != null) {
                    Collections.list(attributeToDelete.getAll()).stream().forEach(x -> context.removeAttributeValue(fieldName, x));
                }
            } else {
                context.setAttributeValue(fieldName, value);
            }
        } catch (NamingException e) {
            // no need to remove an nonexistant attribute
        }
    }

    private class OrgAttributesMapper implements AttributesMapper<Org> {

        private boolean pending;

        OrgAttributesMapper(boolean pending) {
            this.pending = pending;
        }

        public Org mapFromAttributes(Attributes attrs) throws NamingException {
            Org org = new Org();
            org.setId(asStringStream(attrs, "cn").collect(joining(",")));
            org.setName(asStringStream(attrs, "o").collect(joining(",")));
            org.setShortName(asStringStream(attrs, "ou").collect(joining(",")));
            org.setCities(asStringStream(attrs, "description").collect(Collectors.toList()));
            org.setMembers(asStringStream(attrs, "member")
                    .map(raw ->  LdapNameBuilder.newInstance(raw))
                    .map(dn -> dn.build())
                    .map(name -> name.getRdn(name.size() - 1 ).getValue().toString())
                    .collect(Collectors.toList()));
            org.setPending(pending);
            return org;
        }
    }

    private class OrgExtAttributesMapper implements AttributesMapper<OrgExt> {

        private boolean isPending;

        public OrgExtAttributesMapper(boolean isPending) {
            this.isPending = isPending;
        }

        public OrgExt mapFromAttributes(Attributes attrs) throws NamingException {
            OrgExt orgExt = new OrgExt();
            orgExt.setId(asString(attrs.get("o")));
            orgExt.setOrgType(asString(attrs.get("businessCategory")));
            orgExt.setAddress(asString(attrs.get("postalAddress")));
            orgExt.setDescription(asStringStream(attrs,"description").collect(joining(",")));
            orgExt.setPending(isPending);
            return orgExt;
        }
    }

    private class OrgDetailAttributesMapper implements AttributesMapper<OrgDetail> {

        private boolean isPending;

        public OrgDetailAttributesMapper(boolean isPending) {
            this.isPending = isPending;
        }

        public OrgDetail mapFromAttributes(Attributes attrs) throws NamingException {
            OrgDetail org = new OrgDetail();
            org.setId(asString(attrs.get("uid")));
            org.setUrl(asStringStream(attrs,"labeledURI").collect(joining(",")));
            org.setPending(isPending);
            return org;
        }
    }

    public String asString(Attribute att) throws NamingException {
        if(att == null)
            return null;
        else
            return (String) att.get();
    }

    public Stream<String> asStringStream(Attributes attributes, String attributeName) throws NamingException {
        Attribute attribute = attributes.get(attributeName);
        if (attribute == null) {
            return Stream.empty();
        }
        else {
            return Collections.list(attribute.getAll()).stream()
                    .map(Object::toString);
        }
    }

    private class ContextMapperSecuringReferenceAndMappingAttributes<T extends ReferenceAware>  implements ContextMapper<T> {

        private AttributesMapper<T> attributesMapper;

        public ContextMapperSecuringReferenceAndMappingAttributes(AttributesMapper<T> attributesMapper) {
            this.attributesMapper = attributesMapper;
        }

        @Override
        public T mapFromContext(Object o) throws NamingException {
            DirContextAdapter dirContext = (DirContextAdapter) o;
            T dto = attributesMapper.mapFromAttributes(dirContext.getAttributes());
            dto.setReference(dirContext);
            return dto;
        }
    }

    private class OrgContextMapper extends ContextMapperSecuringReferenceAndMappingAttributes<Org> {
        public OrgContextMapper(boolean pending) {
            super(new OrgAttributesMapper(pending));
        }
    }

    private class OrgExtContextMapper extends ContextMapperSecuringReferenceAndMappingAttributes<OrgExt> {
        public OrgExtContextMapper(boolean isPending) {
            super(new OrgExtAttributesMapper(isPending));
        }
    }

    private class OrgDetailContextMapper extends ContextMapperSecuringReferenceAndMappingAttributes<OrgDetail> {
        public OrgDetailContextMapper(boolean isPending) {
            super(new OrgDetailAttributesMapper(isPending));
        }
    }
}
