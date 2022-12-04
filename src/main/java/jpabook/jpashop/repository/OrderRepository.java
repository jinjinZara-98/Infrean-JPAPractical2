package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Order;

import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OrderRepository {

    private final EntityManager em;

    public OrderRepository(EntityManager em) {
        this.em = em;
    }

    public void save(Order order) {
        em.persist(order);
    }

    public Order findOne(Long id) {
        return em.find(Order.class, id);
    }

    public List<Order> findAll() {
        return em.createQuery("select o from Order o", Order.class)
                .getResultList();
    }

    public List<Order> findAllByString(OrderSearch orderSearch) {

            String jpql = "select o from Order o join o.member m";
            boolean isFirstCondition = true;

        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " o.status = :status";
        }

        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            if (isFirstCondition) {
                jpql += " where";
                isFirstCondition = false;
            } else {
                jpql += " and";
            }
            jpql += " m.name like :name";
        }

        TypedQuery<Order> query = em.createQuery(jpql, Order.class)
                .setMaxResults(1000);

        if (orderSearch.getOrderStatus() != null) {
            query = query.setParameter("status", orderSearch.getOrderStatus());
        }
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            query = query.setParameter("name", orderSearch.getMemberName());
        }

        return query.getResultList();
    }

    /**
     * JPA Criteria
     */
    public List<Order> findAllByCriteria(OrderSearch orderSearch) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Order> cq = cb.createQuery(Order.class);
        Root<Order> o = cq.from(Order.class);
        Join<Object, Object> m = o.join("member", JoinType.INNER);

        List<Predicate> criteria = new ArrayList<>();

        //주문 상태 검색
        if (orderSearch.getOrderStatus() != null) {
            Predicate status = cb.equal(o.get("status"), orderSearch.getOrderStatus());
            criteria.add(status);
        }
        //회원 이름 검색
        if (StringUtils.hasText(orderSearch.getMemberName())) {
            Predicate name =
                    cb.like(m.<String>get("name"), "%" + orderSearch.getMemberName() + "%");
            criteria.add(name);
        }

        cq.where(cb.and(criteria.toArray(new Predicate[criteria.size()])));
        TypedQuery<Order> query = em.createQuery(cq).setMaxResults(1000);
        return query.getResultList();
    }

    //order를 가지고 오는 쿼리, member까지 객체 그래프로 한 번에 가져오는
    //sql입장에서는 조인이지만 select로 다 가져오는
    //한 번 쿼리로 다 가져오는, lazy 무시하고 진짜 객체 다 가져오는
    //order클래스에서 회원과 배송 지연로딩으로 되어있지만 무시하고 프록시가 아닌 진짜 객체값을 채워서 가져옴 패치 조인이므로
    //기본으로 지연로딩으로 깔고 필요한거만 패치조인으로 묶어서 db에서 한번으로 가져오면 대부분 성능문제 해결
    //주문을 가지고 오는데 패치조인으로 원하는거만 select
    //외부를 건드리지 않고 내부에 원하는거만 성능 튜닝
    public List<Order> findAllWithMemberDelivery() {//다대일 관계 패치조인하는
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .getResultList();
    }

    //조인을 하면 일대다에서 다만큼 데이터가 뻥튀기됨
    //우리가 원하는건 중복 데이터를 원하지 않음, 그래서 distinct 넣음
    //JPA의 distinct는 db sql문에도 넣어줌, db distinct는 행의 데이터가 다 똑같아야 제거됨
    //그리고 데이터 가져온거중 pk값인 아이디값이 같으면 제거해줌
    //중복을 걸ㄹ 컬렉션에 담아줌, 즉 밑에 쿼리 distinct는 2가지 기능
    public List<Order> findAllWithItem() {
        return em.createQuery(
                "select distinct o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d" +
                        " join fetch o.orderItems oi" +
                        " join fetch oi.item i", Order.class)
                .getResultList();
    }

    public List<Order> findAllWithMemberDelivery(int offset, int limit) {
        return em.createQuery(
                "select o from Order o" +
                        " join fetch o.member m" +
                        " join fetch o.delivery d", Order.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
//페치조인으로는 페이징을 못함
//ToOne관계는 페치조인해도 됨, 데이터 뻥튀기가 안되서
//일대다는 페치조인 X?ㅍ