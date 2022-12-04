package jpabook.jpashop.repository;

import jpabook.jpashop.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

//제네릭은 타입, pk타입(id)
public interface MemberRepository extends JpaRepository<Member, Long> {

    //findByName 처럼 일반화 하기 어려운 기능도 메서드 이름으로 정확한 JPQL 쿼리를 실행한다.
    //코드 이게 끝, select m from Member m where m.name = ?이라고함
    //findBy하고 Name이라 되어있으면 이렇게 where에 조건을 만듬
    List<Member> findByName(String name);
}
